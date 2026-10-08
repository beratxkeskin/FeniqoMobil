package com.feniqo.mobile.presentation.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CreateDebtUseCase
import com.feniqo.mobile.domain.usecase.DeleteDebtUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtUseCase
import com.feniqo.mobile.domain.usecase.UpdateDebtUseCase
import com.feniqo.mobile.domain.validation.DebtBalanceCalculator
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
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
 * Borç / Alacak oluşturma ve düzenleme formunu yöneten ViewModel'dir.
 */
@HiltViewModel
class DebtFormViewModel @Inject constructor(
    private val observeDebtUseCase: ObserveDebtUseCase,
    private val observeDebtPaymentsUseCase: ObserveDebtPaymentsUseCase,
    private val createDebtUseCase: CreateDebtUseCase,
    private val updateDebtUseCase: UpdateDebtUseCase,
    private val deleteDebtUseCase: DeleteDebtUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DebtFormUiState(
            input = DebtFormInput(
                dueDate = currentDateProvider.today(),
            ),
        ),
    )
    private var initialInput: DebtFormInput = _uiState.value.input
    val uiState: StateFlow<DebtFormUiState> = _uiState.asStateFlow()

    private val _editLoadState = MutableStateFlow<DebtEditLoadState>(DebtEditLoadState.Idle)
    val editLoadState: StateFlow<DebtEditLoadState> = _editLoadState.asStateFlow()

    private val _events = Channel<DebtFormUiEvent>(Channel.BUFFERED)
    val events: Flow<DebtFormUiEvent> = _events.receiveAsFlow()

    private var editLoadJob: Job? = null
    private var editLoadToken: Long = 0L

    private var activeSubmitJob: Job? = null
    private var activeDeleteJob: Job? = null

    fun loadDebtForEdit(id: EntityId, decimalSeparator: Char) {
        editLoadJob?.cancel()
        val currentToken = ++editLoadToken
        _editLoadState.value = DebtEditLoadState.Loading
        _uiState.update { it.copy(paymentsHistory = emptyList(), balanceSummary = null) }

        editLoadJob = viewModelScope.launch {
            try {
                combine(
                    observeDebtUseCase(id),
                    observeDebtPaymentsUseCase(id),
                ) { debt, payments ->
                    Pair(debt, payments)
                }.collect { (debt, payments) ->
                    if (editLoadToken == currentToken) {
                        if (debt != null) {
                            val balance = DebtBalanceCalculator.calculate(debt, payments)
                            val draft = DebtFormDraft.fromDomain(debt)
                            val history = payments.toSortedHistoryUiModels()
                            val progressRatio = if (balance.principalAmount.amountMinor > 0L) {
                                (balance.totalPaid.amountMinor.toFloat() / balance.principalAmount.amountMinor.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val summary = DebtBalanceSummaryUiModel(
                                principalAmount = balance.principalAmount,
                                totalPaid = balance.totalPaid,
                                remainingAmount = balance.remainingAmount,
                                isSettled = balance.isSettled,
                                type = debt.type,
                                progressRatio = progressRatio,
                            )
                            val loadedInput = DebtFormInput(
                                debtId = debt.id,
                                titleInput = debt.title,
                                amountInput = formatMoneyToInput(debt.amount, decimalSeparator),
                                currency = debt.amount.currency,
                                type = debt.type,
                                dueDate = debt.dueDate,
                                descriptionInput = debt.description ?: "",
                            )
                            initialInput = loadedInput
                            _uiState.update { state ->
                                state.copy(
                                    input = loadedInput,
                                    errors = DebtFormInputErrors(),
                                    hasUnsavedChanges = false,
                                    paymentsHistory = history,
                                    balanceSummary = summary,
                                )
                            }
                            _editLoadState.value = DebtEditLoadState.Ready(
                                draft = draft,
                                status = balance.status,
                            )
                        } else {
                            _editLoadState.value = DebtEditLoadState.NotFound
                            _uiState.update { it.copy(paymentsHistory = emptyList(), balanceSummary = null) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (editLoadToken == currentToken) {
                    _editLoadState.value = DebtEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
                    _uiState.update { it.copy(paymentsHistory = emptyList(), balanceSummary = null) }
                }
            }
        }
    }

    private fun formatMoneyToInput(money: Money, decimalSeparator: Char): String {
        val digits = money.currency.minorUnitDigits
        val divisor = if (digits == 0) 1L else (1..digits).fold(1L) { acc, _ -> acc * 10L }
        val major = money.amountMinor / divisor
        val minor = money.amountMinor % divisor
        return if (digits > 0 && minor != 0L) {
            val minorStr = minor.toString().padStart(digits, '0').trimEnd('0')
            "$major$decimalSeparator$minorStr"
        } else {
            major.toString()
        }
    }

    fun setEditLoadInvalidId() {
        editLoadJob?.cancel()
        editLoadJob = null
        ++editLoadToken
        _editLoadState.value = DebtEditLoadState.NotFound
        _uiState.update { it.copy(paymentsHistory = emptyList(), balanceSummary = null) }
    }

    fun updateInput(transform: (DebtFormInput) -> DebtFormInput) {
        _uiState.update { state ->
            val updated = transform(state.input)
            val lockedCurrency = if (state.input.isEditMode) state.input.currency else updated.currency
            val lockedDebtId = state.input.debtId
            val protectedInput = updated.copy(
                debtId = lockedDebtId,
                currency = lockedCurrency,
            )
            state.copy(
                input = protectedInput,
                errors = DebtFormInputErrors(),
                hasUnsavedChanges = protectedInput != initialInput,
            )
        }
    }

    fun submit() {
        if (_uiState.value.isSubmitting || activeSubmitJob != null) return

        when (val normResult = _uiState.value.input.toDraft()) {
            is DebtFormNormalizationResult.Invalid -> {
                _uiState.update { it.copy(errors = normResult.errors) }
            }
            is DebtFormNormalizationResult.Valid -> {
                val draft = normResult.draft
                _uiState.update { it.copy(isSubmitting = true) }
                activeSubmitJob = viewModelScope.launch {
                    var mutationSucceeded = false
                    try {
                        val result = if (draft.isCreateMode) {
                            createDebtUseCase(draft.toCreateCommand())
                        } else {
                            updateDebtUseCase(draft.toUpdateCommand())
                        }

                        when (result) {
                            is RepositoryResult.Success -> {
                                mutationSucceeded = true
                                _events.send(DebtFormUiEvent.MutationSuccess(FinanceUiMessage.DEBT_SAVED))
                            }
                            is RepositoryResult.Failure -> {
                                _events.send(DebtFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        _events.send(DebtFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    } finally {
                        if (!mutationSucceeded) _uiState.update { it.copy(isSubmitting = false) }
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
        val targetId = _uiState.value.input.debtId ?: return
        if (_uiState.value.isSubmitting || activeDeleteJob != null) return

        _uiState.update { it.copy(isSubmitting = true, pendingDeleteConfirmation = false) }
        activeDeleteJob = viewModelScope.launch {
            try {
                when (deleteDebtUseCase(targetId)) {
                    is RepositoryResult.Success -> {
                        _events.send(DebtFormUiEvent.MutationSuccess(FinanceUiMessage.DEBT_DELETED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(DebtFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(DebtFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
                activeDeleteJob = null
            }
        }
    }
}
