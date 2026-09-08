package com.feniqo.mobile.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.CalculateMoneyScoreUseCase
import com.feniqo.mobile.domain.usecase.MoneyScoreInput
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveDashboardSummaryUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Dashboard ekranı için veri akışlarını toplayan ve UI durumuna dönüştüren Hilt ViewModel.
 *
 * Sorumluluklar:
 * 1. Yalnızca use-case ve date provider tüketir; doğrudan DAO veya Supabase çağırmaz.
 * 2. İlk seçili ayı CurrentDateProvider üzerinden YYYY-MM olarak dinamik belirler.
 * 3. V1'de TRY para birimi ile çalışır.
 * 4. CancellationException'ı yutmaz ve ham hata detaylarını UI'ya sızdırmaz.
 * 5. MoneyScore hesaplamasını seçili ay işlemleri ve nötr başlangıç puanlarıyla tamamlar.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase,
    private val observeTransactionsUseCase: ObserveTransactionsUseCase,
    private val observeCategoriesForHistoryLookupUseCase: ObserveCategoriesForHistoryLookupUseCase,
    private val calculateMoneyScoreUseCase: CalculateMoneyScoreUseCase,
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val initialMonth: YearMonth by lazy {
        val today = currentDateProvider.today()
        val monthStr = (today.month.ordinal + 1).toString().padStart(2, '0')
        YearMonth("${today.year}-$monthStr")
    }

    private val _selectedMonth = MutableStateFlow<YearMonth?>(null)
    private val _retryTrigger = MutableStateFlow(0L)
    private val _userMessage = MutableStateFlow<FinanceUiMessage?>(null)

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(val dashboard: DashboardDisplayModel) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observationResultFlow: Flow<ObservationResult> = combine(
        _selectedMonth,
        _retryTrigger,
    ) { selected, retryCount ->
        (selected ?: initialMonth) to retryCount
    }.flatMapLatest { (month, _) ->
        combine(
            observeDashboardSummaryUseCase(month = month, currency = Currency.TRY),
            observeTransactionsUseCase(),
            observeCategoriesForHistoryLookupUseCase(),
        ) { summary, transactions, categories ->
            val monthlyTransactions = transactions.filter { it.transactionDate.toString().startsWith(month.value) }
            val moneyScoreInput = MoneyScoreInput(
                income = summary.income,
                expense = summary.expense,
                budgets = emptyList(),
                transactions = monthlyTransactions,
                debts = emptyList(),
                goals = emptyList(),
                today = currentDateProvider.today(),
            )
            val calculatedScore = calculateMoneyScoreUseCase(moneyScoreInput)
            val summaryWithScore = summary.copy(moneyScore = calculatedScore)

            val displayModel = DashboardDisplayModelBuilder.build(
                summary = summaryWithScore,
                transactions = transactions,
                categories = categories,
                moneyScoreIsProvisional = true,
                moneyScoreExplanationText = PROVISIONAL_MONEY_SCORE_EXPLANATION,
            )
            ObservationResult.Success(displayModel) as ObservationResult
        }.catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(ObservationResult.Failure(FinanceUiMessage.GENERIC_ERROR))
        }.onStart {
            emit(ObservationResult.Loading)
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        observationResultFlow,
        _selectedMonth,
        _userMessage,
        observeActiveWorkspaceUseCase(),
    ) { obsResult, selectedMonth, userMessage, activeWorkspace ->
        val currentMonth = selectedMonth ?: initialMonth
        val workspaceName = activeWorkspace?.name
        when (obsResult) {
            is ObservationResult.Loading -> DashboardUiState(
                isLoading = true,
                dashboard = null,
                selectedMonth = currentMonth,
                activeWorkspaceName = workspaceName,
                userMessage = userMessage,
                observationError = null,
            )

            is ObservationResult.Success -> DashboardUiState(
                isLoading = false,
                dashboard = obsResult.dashboard,
                selectedMonth = currentMonth,
                activeWorkspaceName = workspaceName,
                userMessage = userMessage,
                observationError = null,
            )

            is ObservationResult.Failure -> DashboardUiState(
                isLoading = false,
                dashboard = null,
                selectedMonth = currentMonth,
                activeWorkspaceName = workspaceName,
                userMessage = userMessage,
                observationError = obsResult.message,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(
            isLoading = true,
            dashboard = null,
            selectedMonth = null,
            activeWorkspaceName = null,
            userMessage = null,
            observationError = null,
        ),
    )

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    fun retry() {
        _retryTrigger.update { it + 1 }
    }

    fun consumeMessage() {
        _userMessage.value = null
    }

    fun showMessage(message: FinanceUiMessage) {
        _userMessage.value = message
    }

    companion object {
        const val PROVISIONAL_MONEY_SCORE_EXPLANATION =
            "Skor şu anda gelir-gider hareketleri ve henüz kullanılmayan bütçe, borç ve hedef modülleri için nötr başlangıç puanlarıyla hesaplanır."
    }
}
