package com.feniqo.mobile.presentation.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CreateGoalUseCase
import com.feniqo.mobile.domain.usecase.DeleteGoalUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalContributionsUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalUseCase
import com.feniqo.mobile.domain.usecase.UpdateGoalUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Birikim hedefi oluşturma ve düzenleme formunu yöneten ViewModel'dir.
 */
@HiltViewModel
class GoalFormViewModel @Inject constructor(
    private val observeGoalUseCase: ObserveGoalUseCase,
    private val observeGoalContributionsUseCase: ObserveGoalContributionsUseCase,
    private val createGoalUseCase: CreateGoalUseCase,
    private val updateGoalUseCase: UpdateGoalUseCase,
    private val deleteGoalUseCase: DeleteGoalUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GoalFormUiState(
            input = GoalFormInput(
                targetDate = currentDateProvider.today(),
            ),
        ),
    )
    val uiState: StateFlow<GoalFormUiState> = _uiState.asStateFlow()

    private val _editLoadState = MutableStateFlow<GoalEditLoadState>(GoalEditLoadState.Idle)
    val editLoadState: StateFlow<GoalEditLoadState> = _editLoadState.asStateFlow()

    private val _events = Channel<GoalFormUiEvent>(Channel.BUFFERED)
    val events: Flow<GoalFormUiEvent> = _events.receiveAsFlow()

    private var editLoadJob: Job? = null
    private var editLoadToken: Long = 0L

    private var activeSubmitJob: Job? = null
    private var activeDeleteJob: Job? = null

    fun loadGoalForEdit(id: EntityId) {
        editLoadJob?.cancel()
        val currentToken = ++editLoadToken
        _editLoadState.value = GoalEditLoadState.Loading
        _uiState.update { it.copy(contributionsHistory = emptyList()) }

        editLoadJob = viewModelScope.launch {
            try {
                combine(
                    observeGoalUseCase(id),
                    observeGoalContributionsUseCase(id),
                ) { goal, contributions ->
                    Pair(goal, contributions)
                }.collect { (goal, contributions) ->
                    if (editLoadToken == currentToken) {
                        if (goal != null) {
                            val draft = GoalFormDraft.fromDomain(goal)
                            val history = contributions.toSortedHistoryUiModels()
                            _editLoadState.value = GoalEditLoadState.Ready(
                                draft = draft,
                                currentAmount = goal.currentAmount,
                            )
                            _uiState.update { state ->
                                state.copy(
                                    input = GoalFormInput(
                                        goalId = goal.id,
                                        nameInput = goal.name,
                                        targetAmountInput = formatMoneyToInput(goal.targetAmount),
                                        currency = goal.targetAmount.currency,
                                        initialAmountInput = "", // Editte başlangıç girilemez
                                        targetDate = goal.targetDate,
                                        colorHex = goal.color.hex,
                                        iconKey = goal.icon?.key ?: "savings",
                                    ),
                                    errors = GoalFormInputErrors(),
                                    contributionsHistory = history,
                                )
                            }
                        } else {
                            _editLoadState.value = GoalEditLoadState.NotFound
                            _uiState.update { it.copy(contributionsHistory = emptyList()) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (editLoadToken == currentToken) {
                    _editLoadState.value = GoalEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
                    _uiState.update { it.copy(contributionsHistory = emptyList()) }
                }
            }
        }
    }

    private fun formatMoneyToInput(money: com.feniqo.mobile.domain.model.Money): String {
        val digits = money.currency.minorUnitDigits
        val divisor = if (digits == 0) 1L else (1..digits).fold(1L) { acc, _ -> acc * 10L }
        val major = money.amountMinor / divisor
        val minor = money.amountMinor % divisor
        return if (digits > 0 && minor != 0L) {
            val minorStr = minor.toString().padStart(digits, '0').trimEnd('0')
            "$major,$minorStr"
        } else {
            major.toString()
        }
    }


    fun setEditLoadInvalidId() {
        editLoadJob?.cancel()
        editLoadJob = null
        ++editLoadToken
        _editLoadState.value = GoalEditLoadState.NotFound
        _uiState.update { it.copy(contributionsHistory = emptyList()) }
    }


    fun updateInput(transform: (GoalFormInput) -> GoalFormInput) {
        _uiState.update { state ->
            val updated = transform(state.input)
            val lockedCurrency = if (state.input.isEditMode) state.input.currency else updated.currency
            val lockedGoalId = state.input.goalId
            state.copy(
                input = updated.copy(
                    goalId = lockedGoalId,
                    currency = lockedCurrency,
                ),
                errors = GoalFormInputErrors(), // Reset errors on user modification
            )
        }
    }

    fun submit() {
        if (_uiState.value.isSubmitting || activeSubmitJob != null) return

        when (val normResult = _uiState.value.input.toDraft()) {
            is GoalFormNormalizationResult.Invalid -> {
                _uiState.update { it.copy(errors = normResult.errors) }
            }
            is GoalFormNormalizationResult.Valid -> {
                val draft = normResult.draft
                _uiState.update { it.copy(isSubmitting = true) }
                activeSubmitJob = viewModelScope.launch {
                    try {
                        val result = if (draft.isCreateMode) {
                            createGoalUseCase(draft.toCreateCommand())
                        } else {
                            updateGoalUseCase(draft.toUpdateCommand())
                        }

                        when (result) {
                            is RepositoryResult.Success -> {
                                _events.send(GoalFormUiEvent.MutationSuccess(FinanceUiMessage.GOAL_SAVED))
                            }
                            is RepositoryResult.Failure -> {
                                _events.send(GoalFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        _events.send(GoalFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    } finally {
                        _uiState.update { it.copy(isSubmitting = false) }
                        activeSubmitJob = null
                    }
                }
            }
        }
    }

    fun requestDelete() {
        if (_uiState.value.input.isEditMode) {
            _uiState.update { it.copy(pendingDeleteConfirmation = true) }
        }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(pendingDeleteConfirmation = false) }
    }

    fun confirmDelete() {
        val targetId = _uiState.value.input.goalId ?: return
        if (_uiState.value.isSubmitting || activeDeleteJob != null) return

        _uiState.update { it.copy(isSubmitting = true, pendingDeleteConfirmation = false) }
        activeDeleteJob = viewModelScope.launch {
            try {
                when (deleteGoalUseCase(targetId)) {
                    is RepositoryResult.Success -> {
                        _events.send(GoalFormUiEvent.MutationSuccess(FinanceUiMessage.GOAL_DELETED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(GoalFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(GoalFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
                activeDeleteJob = null
            }
        }
    }
}
