package com.feniqo.mobile.presentation.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtsUseCase
import com.feniqo.mobile.domain.validation.DebtBalanceCalculator
import com.feniqo.mobile.domain.validation.DebtSnowballPlanner
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Borç Snowball simülasyon ekranının durumunu ve hesaplama mantığını yöneten ViewModel'dir.
 * Kalıcı veri değişikliği yapmaz; Room SSOT'tan gelen borçları reaktif ve fail-closed gözlemler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtSnowballPlanViewModel @Inject constructor(
    private val observeDebtsUseCase: ObserveDebtsUseCase,
    private val observeDebtPaymentsUseCase: ObserveDebtPaymentsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebtSnowballPlanUiState())
    val uiState: StateFlow<DebtSnowballPlanUiState> = _uiState.asStateFlow()

    private val _events = Channel<DebtSnowballPlanUiEvent>(Channel.BUFFERED)
    val events: Flow<DebtSnowballPlanUiEvent> = _events.receiveAsFlow()

    private var latestDebts: List<Debt> = emptyList()
    private var latestPayments: List<DebtPayment> = emptyList()
    private var observationJob: Job? = null

    init {
        startObservation()
    }

    fun retry() {
        startObservation()
    }

    private fun startObservation() {
        observationJob?.cancel()
        _uiState.update { it.copy(isLoadingDebts = true, observationError = null) }

        observationJob = viewModelScope.launch {
            try {
                observeDebtsUseCase()
                    .flatMapLatest { debts ->
                        if (debts.isEmpty()) {
                            flowOf(Pair(debts, emptyList()))
                        } else {
                            val paymentFlows = debts.map { debt -> observeDebtPaymentsUseCase(debt.id) }
                            combine(paymentFlows) { paymentsArray ->
                                Pair(debts, paymentsArray.flatMap { it })
                            }
                        }
                    }
                    .collect { (debts, payments) ->
                        processDebtsSnapshot(debts, payments)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                latestDebts = emptyList()
                latestPayments = emptyList()
                _uiState.update {
                    it.copy(
                        isLoadingDebts = false,
                        observationError = FinanceUiMessage.GENERIC_ERROR,
                        eligibleDebtsCount = 0,
                        plan = null,
                    )
                }
            }
        }
    }

    private fun processDebtsSnapshot(debts: List<Debt>, payments: List<DebtPayment>) {
        try {
            val paymentsByDebtId = payments.groupBy { it.debtId }
            val eligibleDebts = mutableListOf<Debt>()

            for (debt in debts) {
                val debtPayments = paymentsByDebtId[debt.id] ?: emptyList()
                val balance = DebtBalanceCalculator.calculate(debt, debtPayments)
                if (debt.type == DebtType.DEBT && debt.status != DebtStatus.SETTLED && balance.remainingAmount.amountMinor > 0) {
                    eligibleDebts.add(debt)
                }
            }

            latestDebts = debts
            latestPayments = payments

            val availableCurrencies = if (eligibleDebts.isNotEmpty()) {
                eligibleDebts.map { it.amount.currency }.distinct().sortedBy { it.name }
            } else {
                listOf(Currency.TRY, Currency.USD, Currency.EUR)
            }

            _uiState.update { state ->
                val selected = if (availableCurrencies.contains(state.selectedCurrency)) {
                    state.selectedCurrency
                } else {
                    availableCurrencies.firstOrNull() ?: Currency.TRY
                }
                val count = eligibleDebts.count { it.amount.currency == selected }

                state.copy(
                    availableCurrencies = availableCurrencies,
                    selectedCurrency = selected,
                    eligibleDebtsCount = count,
                    isLoadingDebts = false,
                    observationError = null,
                    plan = null, // SSOT değiştiğinde eski plan geçersiz kılınır
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            latestDebts = emptyList()
            latestPayments = emptyList()
            _uiState.update {
                it.copy(
                    isLoadingDebts = false,
                    observationError = FinanceUiMessage.GENERIC_ERROR,
                    eligibleDebtsCount = 0,
                    plan = null,
                )
            }
        }
    }

    fun selectCurrency(currency: Currency) {
        if (_uiState.value.observationError != null) return

        try {
            val paymentsByDebtId = latestPayments.groupBy { it.debtId }
            val count = latestDebts.count { debt ->
                if (debt.type != DebtType.DEBT || debt.status == DebtStatus.SETTLED || debt.amount.currency != currency) return@count false
                val debtPayments = paymentsByDebtId[debt.id] ?: emptyList()
                val balance = DebtBalanceCalculator.calculate(debt, debtPayments)
                balance.remainingAmount.amountMinor > 0
            }

            _uiState.update {
                it.copy(
                    selectedCurrency = currency,
                    eligibleDebtsCount = count,
                    budgetError = null,
                    plan = null, // Para birimi değiştiğinde eski plan temizlenir
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    observationError = FinanceUiMessage.GENERIC_ERROR,
                    eligibleDebtsCount = 0,
                    plan = null,
                )
            }
        }
    }

    fun updateBudgetInput(input: String) {
        _uiState.update {
            it.copy(
                budgetInput = input,
                budgetError = null,
                plan = null, // Bütçe girdisi değiştiğinde eski plan temizlenir
            )
        }
    }

    fun calculatePlan() {
        val currentState = _uiState.value
        if (currentState.observationError != null) return

        val currency = currentState.selectedCurrency

        when (val parseResult = MoneyAmountParser.parseToMinorUnits(currentState.budgetInput, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                val budget = Money(parseResult.amountMinor, currency)
                val targetDebts = latestDebts.filter {
                    it.type == DebtType.DEBT && it.amount.currency == currency && it.status != DebtStatus.SETTLED
                }
                val targetDebtIds = targetDebts.map { it.id }.toSet()
                val targetPayments = latestPayments.filter { targetDebtIds.contains(it.debtId) }

                try {
                    val plan = DebtSnowballPlanner.calculate(
                        debts = targetDebts,
                        payments = targetPayments,
                        monthlyPaymentBudget = budget,
                    )
                    val displayModel = plan.toDisplayUiModel(targetDebts.associateBy { it.id })
                    _uiState.update { it.copy(plan = displayModel, budgetError = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _uiState.update { it.copy(plan = null) }
                    viewModelScope.launch {
                        _events.send(DebtSnowballPlanUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                    }
                }
            }
            is MoneyAmountParser.ParseResult.Invalid -> {
                val errorMessage = when (parseResult.error) {
                    MoneyAmountParser.MoneyParseError.EMPTY -> "Aylık bütçe zorunludur."
                    MoneyAmountParser.MoneyParseError.NON_POSITIVE -> "Aylık bütçe sıfırdan büyük olmalıdır."
                    MoneyAmountParser.MoneyParseError.MAX_AMOUNT_EXCEEDED -> "Aylık bütçe çok yüksek."
                    MoneyAmountParser.MoneyParseError.INVALID_FORMAT,
                    MoneyAmountParser.MoneyParseError.EXCESSIVE_DECIMAL_DIGITS -> "Geçerli bir tutar girin."
                }
                _uiState.update { it.copy(budgetError = errorMessage, plan = null) }
            }
        }
    }
}

