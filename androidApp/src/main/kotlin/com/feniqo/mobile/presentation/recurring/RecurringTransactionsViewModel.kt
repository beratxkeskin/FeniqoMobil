package com.feniqo.mobile.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CreateRecurringTransactionUseCase
import com.feniqo.mobile.domain.usecase.DeleteRecurringTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionsUseCase
import com.feniqo.mobile.domain.usecase.SetRecurringTransactionActiveUseCase
import com.feniqo.mobile.domain.usecase.UpdateRecurringTransactionUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tekrarlayan işlem kurallarının gözlemini, yeniden deneme (retry) ve CRUD mutasyon niyetlerini yöneten ViewModel'dir.
 */
@HiltViewModel
class RecurringTransactionsViewModel @Inject constructor(
    private val observeRecurringTransactionsUseCase: ObserveRecurringTransactionsUseCase,
    private val observeRecurringTransactionUseCase: ObserveRecurringTransactionUseCase,
    private val observeCategoriesUseCase: ObserveCategoriesUseCase,
    private val createRecurringTransactionUseCase: CreateRecurringTransactionUseCase,
    private val updateRecurringTransactionUseCase: UpdateRecurringTransactionUseCase,
    private val setRecurringTransactionActiveUseCase: SetRecurringTransactionActiveUseCase,
    private val deleteRecurringTransactionUseCase: DeleteRecurringTransactionUseCase,
) : ViewModel() {

    private val _retryTrigger = MutableStateFlow(0L)
    private val _mutationState = MutableStateFlow(RecurringTransactionMutationState())

    private val _editLoadState = MutableStateFlow<RecurringTransactionEditLoadState>(RecurringTransactionEditLoadState.Idle)
    val editLoadState: StateFlow<RecurringTransactionEditLoadState> = _editLoadState.asStateFlow()

    private var editLoadJob: Job? = null
    private var editLoadToken: Long = 0L

    private val _events = Channel<RecurringTransactionUiEvent>(Channel.BUFFERED)
    val events: Flow<RecurringTransactionUiEvent> = _events.receiveAsFlow()

    private var activeMutationJob: Job? = null

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(val items: List<RecurringTransactionDisplayModel>) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observationResultFlow: Flow<ObservationResult> = _retryTrigger
        .flatMapLatest {
            combine<List<RecurringTransaction>, List<Category>, ObservationResult>(
                observeRecurringTransactionsUseCase(),
                observeCategoriesUseCase(),
            ) { recurringList, categories ->
                val items = RecurringTransactionDisplayModelMapper.map(
                    recurringTransactions = recurringList,
                    categories = categories,
                )
                ObservationResult.Success(items)
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

    val uiState: StateFlow<RecurringTransactionsUiState> = combine(
        observationResultFlow,
        _mutationState,
    ) { observationResult, mutationState ->
        when (observationResult) {
            is ObservationResult.Loading -> RecurringTransactionsUiState(
                isLoading = true,
                items = emptyList(),
                observationError = null,
                mutationState = mutationState,
            )
            is ObservationResult.Failure -> RecurringTransactionsUiState(
                isLoading = false,
                items = emptyList(),
                observationError = observationResult.message,
                mutationState = mutationState,
            )
            is ObservationResult.Success -> RecurringTransactionsUiState(
                isLoading = false,
                items = observationResult.items,
                observationError = null,
                mutationState = mutationState,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RecurringTransactionsUiState(isLoading = true),
    )

    fun onIntent(intent: RecurringTransactionsIntent) {
        when (intent) {
            is RecurringTransactionsIntent.Retry -> retryObservation()
            is RecurringTransactionsIntent.Create -> handleCreate(intent.command)
            is RecurringTransactionsIntent.Update -> handleUpdate(intent.command)
            is RecurringTransactionsIntent.SetActive -> handleSetActive(intent.command)
            is RecurringTransactionsIntent.RequestDelete -> handleRequestDelete(intent.id)
            is RecurringTransactionsIntent.ConfirmDelete -> handleConfirmDelete()
            is RecurringTransactionsIntent.DismissDelete -> handleDismissDelete()
        }
    }

    fun retryObservation() {
        _retryTrigger.update { it + 1 }
    }

    fun loadRecurringForEdit(id: EntityId) {
        editLoadJob?.cancel()
        val currentToken = ++editLoadToken
        _editLoadState.value = RecurringTransactionEditLoadState.Loading
        editLoadJob = viewModelScope.launch {
            try {
                observeRecurringTransactionUseCase(id).collect { recurring ->
                    if (editLoadToken == currentToken) {
                        _editLoadState.value = if (recurring != null) {
                            RecurringTransactionEditLoadState.Ready(
                                draft = RecurringTransactionFormDraft.fromDomain(recurring),
                                isActive = recurring.isActive,
                            )
                        } else {
                            RecurringTransactionEditLoadState.NotFound
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (editLoadToken == currentToken) {
                    _editLoadState.value = RecurringTransactionEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
                }
            }
        }
    }

    fun setEditLoadInvalidId() {
        editLoadJob?.cancel()
        editLoadJob = null
        ++editLoadToken
        _editLoadState.value = RecurringTransactionEditLoadState.NotFound
    }

    private fun handleCreate(command: CreateRecurringTransactionCommand) {
        if (_mutationState.value.isSubmitting || activeMutationJob != null) return
        _mutationState.update { it.copy(isSubmitting = true) }
        activeMutationJob = viewModelScope.launch {
            try {
                when (createRecurringTransactionUseCase(command)) {
                    is RepositoryResult.Success -> {
                        _events.send(RecurringTransactionUiEvent.MutationSuccess(FinanceUiMessage.TRANSACTION_SAVED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
                activeMutationJob = null
            }
        }
    }

    private fun handleUpdate(command: UpdateRecurringTransactionCommand) {
        if (_mutationState.value.isSubmitting || activeMutationJob != null) return
        _mutationState.update { it.copy(isSubmitting = true) }
        activeMutationJob = viewModelScope.launch {
            try {
                when (updateRecurringTransactionUseCase(command)) {
                    is RepositoryResult.Success -> {
                        _events.send(RecurringTransactionUiEvent.MutationSuccess(FinanceUiMessage.TRANSACTION_SAVED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
                activeMutationJob = null
            }
        }
    }

    private fun handleSetActive(command: SetRecurringTransactionActiveCommand) {
        if (_mutationState.value.isSubmitting || activeMutationJob != null) return
        _mutationState.update { it.copy(isSubmitting = true) }
        activeMutationJob = viewModelScope.launch {
            try {
                when (setRecurringTransactionActiveUseCase(command)) {
                    is RepositoryResult.Success -> {
                        _events.send(RecurringTransactionUiEvent.MutationSuccess(FinanceUiMessage.TRANSACTION_SAVED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
                activeMutationJob = null
            }
        }
    }

    private fun handleRequestDelete(id: EntityId) {
        _mutationState.update { it.copy(pendingDeleteId = id) }
    }

    private fun handleDismissDelete() {
        _mutationState.update { it.copy(pendingDeleteId = null) }
    }

    private fun handleConfirmDelete() {
        val targetId = _mutationState.value.pendingDeleteId ?: return
        if (_mutationState.value.isSubmitting || activeMutationJob != null) return
        _mutationState.update { it.copy(isSubmitting = true) }
        activeMutationJob = viewModelScope.launch {
            try {
                when (deleteRecurringTransactionUseCase(targetId)) {
                    is RepositoryResult.Success -> {
                        _mutationState.update { it.copy(pendingDeleteId = null) }
                        _events.send(RecurringTransactionUiEvent.MutationSuccess(FinanceUiMessage.TRANSACTION_DELETED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
                activeMutationJob = null
            }
        }
    }
}
