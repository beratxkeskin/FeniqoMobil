package com.feniqo.mobile.presentation.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtsUseCase
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Borç ve alacakların gözlemini ve yeniden deneme (retry) niyetini yöneten ViewModel'dir.
 */
@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val observeDebtsUseCase: ObserveDebtsUseCase,
    private val observeDebtPaymentsUseCase: ObserveDebtPaymentsUseCase,
) : ViewModel() {

    private val _retryTrigger = MutableStateFlow(0L)

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(val debts: List<DebtDisplayModel>) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observationResultFlow: Flow<ObservationResult> = _retryTrigger
        .flatMapLatest {
            observeDebtsUseCase()
                .flatMapLatest { debts ->
                    if (debts.isEmpty()) {
                        flowOf(ObservationResult.Success(emptyList()) as ObservationResult)
                    } else {
                        val paymentFlows = debts.map { debt ->
                            observeDebtPaymentsUseCase(debt.id).map { payments -> debt.id to payments }
                        }
                        combine(paymentFlows) { pairs ->
                            val paymentsByDebtId = pairs.toMap()
                            val items = DebtDisplayModelMapper.map(debts, paymentsByDebtId)
                            ObservationResult.Success(items) as ObservationResult
                        }
                    }
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

    val uiState: StateFlow<DebtsUiState> = observationResultFlow
        .map { observationResult ->
            when (observationResult) {
                is ObservationResult.Loading -> DebtsUiState(
                    isLoading = true,
                    debts = emptyList(),
                    observationError = null,
                )
                is ObservationResult.Failure -> DebtsUiState(
                    isLoading = false,
                    debts = emptyList(),
                    observationError = observationResult.message,
                )
                is ObservationResult.Success -> DebtsUiState(
                    isLoading = false,
                    debts = observationResult.debts,
                    observationError = null,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DebtsUiState(isLoading = true),
        )

    fun onIntent(intent: DebtsIntent) {
        when (intent) {
            is DebtsIntent.Retry -> retryObservation()
        }
    }

    private fun retryObservation() {
        _retryTrigger.update { it + 1 }
    }
}
