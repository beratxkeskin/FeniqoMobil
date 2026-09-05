package com.feniqo.mobile.presentation.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.AddDebtPaymentUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtUseCase
import com.feniqo.mobile.domain.validation.DebtBalanceCalculator
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebtPaymentFormViewModel @Inject constructor(
    private val observeDebtUseCase: ObserveDebtUseCase,
    private val observeDebtPaymentsUseCase: ObserveDebtPaymentsUseCase,
    private val addDebtPaymentUseCase: AddDebtPaymentUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val _parentLoadState = MutableStateFlow<DebtPaymentParentLoadState>(DebtPaymentParentLoadState.Idle)
    val parentLoadState: StateFlow<DebtPaymentParentLoadState> = _parentLoadState.asStateFlow()

    private val _uiState = MutableStateFlow(
        DebtPaymentFormUiState(
            input = DebtPaymentFormInput(paidOn = currentDateProvider.today()),
        ),
    )
    val uiState: StateFlow<DebtPaymentFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<DebtPaymentFormUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var parentDebt: Debt? = null
    private var existingPayments: List<DebtPayment> = emptyList()
    private var parentLoadJob: Job? = null
    private var parentLoadToken: Long = 0L

    fun loadParentDebt(debtId: EntityId) {
        parentLoadJob?.cancel()
        val currentToken = ++parentLoadToken

        _parentLoadState.value = DebtPaymentParentLoadState.Loading

        parentLoadJob = viewModelScope.launch {
            try {
                combine(
                    observeDebtUseCase(debtId),
                    observeDebtPaymentsUseCase(debtId),
                ) { debt, payments ->
                    debt to payments
                }.collect { (debt, payments) ->
                    if (parentLoadToken == currentToken) {
                        parentDebt = debt
                        existingPayments = payments
                        if (debt != null) {
                            val balance = DebtBalanceCalculator.calculate(debt, payments)
                            _parentLoadState.value = DebtPaymentParentLoadState.Ready(
                                debt = debt,
                                remainingAmount = balance.remainingAmount,
                                isSettled = balance.status == DebtStatus.SETTLED || balance.remainingAmount.amountMinor <= 0L,
                            )
                            if (_uiState.value.input.paidOn == null) {
                                _uiState.update { current ->
                                    current.copy(
                                        input = current.input.copy(paidOn = currentDateProvider.today()),
                                    )
                                }
                            }
                        } else {
                            _parentLoadState.value = DebtPaymentParentLoadState.NotFound
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (parentLoadToken == currentToken) {
                    _parentLoadState.value = DebtPaymentParentLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
                }
            }
        }
    }

    fun setParentLoadInvalidId() {
        parentLoadJob?.cancel()
        parentLoadJob = null
        parentDebt = null
        existingPayments = emptyList()
        parentLoadToken++
        _parentLoadState.value = DebtPaymentParentLoadState.NotFound
    }

    fun updateInput(transform: (DebtPaymentFormInput) -> DebtPaymentFormInput) {
        _uiState.update { current ->
            val updated = transform(current.input)
            current.copy(
                input = updated,
                errors = DebtPaymentFormInputErrors(),
            )
        }
    }

    fun submit() {
        val currentState = _uiState.value
        if (currentState.isSubmitting) return

        val currentParent = parentDebt
        if (currentParent == null) {
            _uiState.update {
                it.copy(errors = DebtPaymentFormInputErrors(generalError = DebtPaymentFormFieldError.PARENT_DEBT_MISSING))
            }
            return
        }

        when (val draftResult = currentState.input.toDraft(currentParent, existingPayments)) {
            is DebtPaymentFormNormalizationResult.Invalid -> {
                _uiState.update { it.copy(errors = draftResult.errors) }
            }
            is DebtPaymentFormNormalizationResult.Success -> {
                _uiState.update { it.copy(isSubmitting = true, errors = DebtPaymentFormInputErrors()) }
                viewModelScope.launch {
                    try {
                        val result = addDebtPaymentUseCase(draftResult.command)
                        when (result) {
                            is RepositoryResult.Success -> {
                                _events.send(DebtPaymentFormUiEvent.MutationSuccess(FinanceUiMessage.DEBT_PAYMENT_ADDED))
                            }
                            is RepositoryResult.Failure -> {
                                _events.send(DebtPaymentFormUiEvent.ShowMessage(result.error.toFinanceUiMessage()))
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        _events.send(DebtPaymentFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    } finally {
                        _uiState.update { it.copy(isSubmitting = false) }
                    }
                }

            }
        }
    }
}
