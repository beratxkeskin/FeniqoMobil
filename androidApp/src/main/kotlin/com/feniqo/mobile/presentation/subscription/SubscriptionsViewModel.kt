package com.feniqo.mobile.presentation.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.AdvanceSubscriptionRenewalUseCase
import com.feniqo.mobile.domain.usecase.CreateSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.DeleteSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionsUseCase
import com.feniqo.mobile.domain.usecase.SetSubscriptionActiveUseCase
import com.feniqo.mobile.domain.usecase.UpdateSubscriptionUseCase
import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult
import com.feniqo.mobile.presentation.common.CurrentDateProvider
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
 * Aboneliklerin gözlemini, yeniden deneme (retry) ve CRUD mutasyon niyetlerini yöneten ViewModel'dir.
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val observeSubscriptionsUseCase: ObserveSubscriptionsUseCase,
    private val observeSubscriptionUseCase: ObserveSubscriptionUseCase,
    private val observeCategoriesUseCase: ObserveCategoriesUseCase,
    private val createSubscriptionUseCase: CreateSubscriptionUseCase,
    private val updateSubscriptionUseCase: UpdateSubscriptionUseCase,
    private val setSubscriptionActiveUseCase: SetSubscriptionActiveUseCase,
    private val advanceSubscriptionRenewalUseCase: AdvanceSubscriptionRenewalUseCase,
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {


    private val _retryTrigger = MutableStateFlow(0L)
    private val _mutationState = MutableStateFlow(SubscriptionMutationState())

    private val _editLoadState = MutableStateFlow<SubscriptionEditLoadState>(SubscriptionEditLoadState.Idle)
    val editLoadState: StateFlow<SubscriptionEditLoadState> = _editLoadState.asStateFlow()

    private var editLoadJob: Job? = null
    private var editLoadToken: Long = 0L

    private val _events = Channel<SubscriptionUiEvent>(Channel.BUFFERED)
    val events: Flow<SubscriptionUiEvent> = _events.receiveAsFlow()

    private var activeMutationJob: Job? = null

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(val items: List<SubscriptionDisplayModel>) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observationResultFlow: Flow<ObservationResult> = _retryTrigger
        .flatMapLatest {
            combine<List<Subscription>, List<Category>, ObservationResult>(
                observeSubscriptionsUseCase(),
                observeCategoriesUseCase(),
            ) { subscriptionList, categories ->
                val items = SubscriptionDisplayModelMapper.map(
                    subscriptions = subscriptionList,
                    categories = categories,
                    today = currentDateProvider.today(),
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

    val uiState: StateFlow<SubscriptionsUiState> = combine(
        observationResultFlow,
        _mutationState,
    ) { observationResult, mutationState ->
        when (observationResult) {
            is ObservationResult.Loading -> SubscriptionsUiState(
                isLoading = true,
                items = emptyList(),
                observationError = null,
                mutationState = mutationState,
            )
            is ObservationResult.Failure -> SubscriptionsUiState(
                isLoading = false,
                items = emptyList(),
                observationError = observationResult.message,
                mutationState = mutationState,
            )
            is ObservationResult.Success -> SubscriptionsUiState(
                isLoading = false,
                items = observationResult.items,
                observationError = null,
                mutationState = mutationState,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SubscriptionsUiState(isLoading = true),
    )

    fun onIntent(intent: SubscriptionsIntent) {
        when (intent) {
            is SubscriptionsIntent.Retry -> retryObservation()
            is SubscriptionsIntent.Create -> handleCreate(intent.command)
            is SubscriptionsIntent.Update -> handleUpdate(intent.command)
            is SubscriptionsIntent.SetActive -> handleSetActive(intent.command)
            is SubscriptionsIntent.RequestDelete -> handleRequestDelete(intent.id)
            is SubscriptionsIntent.ConfirmDelete -> handleConfirmDelete()
            is SubscriptionsIntent.DismissDelete -> handleDismissDelete()
            is SubscriptionsIntent.RequestAdvanceRenewal -> handleRequestAdvanceRenewal(intent.id, intent.nextRenewalDate)
            is SubscriptionsIntent.ConfirmAdvanceRenewal -> handleConfirmAdvanceRenewal()
            is SubscriptionsIntent.DismissAdvanceRenewal -> handleDismissAdvanceRenewal()
        }
    }


    fun retryObservation() {
        _retryTrigger.update { it + 1 }
    }

    fun loadSubscriptionForEdit(id: EntityId) {
        editLoadJob?.cancel()
        val currentToken = ++editLoadToken
        _editLoadState.value = SubscriptionEditLoadState.Loading
        editLoadJob = viewModelScope.launch {
            try {
                observeSubscriptionUseCase(id).collect { subscription ->
                    if (editLoadToken == currentToken) {
                        _editLoadState.value = if (subscription != null) {
                            SubscriptionEditLoadState.Ready(
                                draft = SubscriptionFormDraft.fromDomain(subscription),
                                isActive = subscription.isActive,
                            )
                        } else {
                            SubscriptionEditLoadState.NotFound
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (editLoadToken == currentToken) {
                    _editLoadState.value = SubscriptionEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
                }
            }
        }
    }

    fun setEditLoadInvalidId() {
        editLoadJob?.cancel()
        editLoadJob = null
        ++editLoadToken
        _editLoadState.value = SubscriptionEditLoadState.NotFound
    }

    private fun handleCreate(command: CreateSubscriptionCommand) {
        if (_mutationState.value.isSubmitting || activeMutationJob != null) return
        _mutationState.update { it.copy(isSubmitting = true) }
        activeMutationJob = viewModelScope.launch {
            try {
                when (createSubscriptionUseCase(command)) {
                    is RepositoryResult.Success -> {
                        _events.send(SubscriptionUiEvent.MutationSuccess(FinanceUiMessage.SUBSCRIPTION_SAVED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
                activeMutationJob = null
            }
        }
    }

    private fun handleUpdate(command: UpdateSubscriptionCommand) {
        if (_mutationState.value.isSubmitting || activeMutationJob != null) return
        _mutationState.update { it.copy(isSubmitting = true) }
        activeMutationJob = viewModelScope.launch {
            try {
                when (updateSubscriptionUseCase(command)) {
                    is RepositoryResult.Success -> {
                        _events.send(SubscriptionUiEvent.MutationSuccess(FinanceUiMessage.SUBSCRIPTION_SAVED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
                activeMutationJob = null
            }
        }
    }

    private fun handleSetActive(command: SetSubscriptionActiveCommand) {
        if (_mutationState.value.isSubmitting || activeMutationJob != null) return
        _mutationState.update { it.copy(isSubmitting = true) }
        activeMutationJob = viewModelScope.launch {
            try {
                when (setSubscriptionActiveUseCase(command)) {
                    is RepositoryResult.Success -> {
                        _events.send(SubscriptionUiEvent.MutationSuccess(FinanceUiMessage.SUBSCRIPTION_SAVED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
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
                when (deleteSubscriptionUseCase(targetId)) {
                    is RepositoryResult.Success -> {
                        _mutationState.update { it.copy(pendingDeleteId = null) }
                        _events.send(SubscriptionUiEvent.MutationSuccess(FinanceUiMessage.SUBSCRIPTION_DELETED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
                activeMutationJob = null
            }
        }
    }

    private fun handleRequestAdvanceRenewal(id: EntityId, nextRenewalDate: LocalDate) {
        _mutationState.update { it.copy(pendingAdvanceRenewal = PendingAdvanceRenewalTarget(id, nextRenewalDate)) }
    }

    private fun handleDismissAdvanceRenewal() {
        _mutationState.update { it.copy(pendingAdvanceRenewal = null) }
    }

    private fun handleConfirmAdvanceRenewal() {
        val target = _mutationState.value.pendingAdvanceRenewal ?: return
        if (_mutationState.value.isSubmitting || activeMutationJob != null) return
        _mutationState.update { it.copy(isSubmitting = true) }
        activeMutationJob = viewModelScope.launch {
            try {
                when (val result = advanceSubscriptionRenewalUseCase(target.id)) {
                    is RepositoryResult.Success -> {
                        _mutationState.update { it.copy(pendingAdvanceRenewal = null) }
                        val successMessage = when (result.value) {
                            is SubscriptionRenewalProgressionResult.Advanced -> FinanceUiMessage.SUBSCRIPTION_RENEWED
                            is SubscriptionRenewalProgressionResult.Completed -> FinanceUiMessage.SUBSCRIPTION_COMPLETED
                            is SubscriptionRenewalProgressionResult.InactiveSubscription -> FinanceUiMessage.GENERIC_ERROR
                        }
                        _events.send(SubscriptionUiEvent.MutationSuccess(successMessage))
                    }
                    is RepositoryResult.Failure -> {

                        _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(SubscriptionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
                activeMutationJob = null
            }
        }
    }

}

