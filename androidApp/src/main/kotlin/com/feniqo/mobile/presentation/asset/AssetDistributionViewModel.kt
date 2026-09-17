package com.feniqo.mobile.presentation.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.usecase.ObserveAssetsUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetDistributionViewModel @Inject constructor(
    private val observeAssetsUseCase: ObserveAssetsUseCase,
) : ViewModel() {

    private val selectedCurrencyFlow = MutableStateFlow<Currency?>(null)
    private val retryTrigger = MutableStateFlow(0L)

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(
            val assets: List<Asset>,
        ) : ObservationResult
        data object Failure : ObservationResult
    }

    private val assetsObservation: Flow<ObservationResult> = retryTrigger.flatMapLatest {
        observeAssetsUseCase()
            .map { ObservationResult.Success(it) as ObservationResult }
            .onStart { emit(ObservationResult.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(ObservationResult.Failure)
            }
    }

    val uiState: StateFlow<AssetDistributionUiState> = combine(
        assetsObservation,
        selectedCurrencyFlow,
    ) { result, explicitCurrency ->
        when (result) {
            ObservationResult.Loading -> AssetDistributionUiState(isLoading = true)
            ObservationResult.Failure -> AssetDistributionUiState(
                isLoading = false,
                observationError = FinanceUiMessage.GENERIC_ERROR,
            )
            is ObservationResult.Success -> {
                val assets = result.assets
                val availableCurrencies = assets
                    .map { it.currentValue.currency }
                    .distinct()
                    .sortedBy { it.ordinal }

                val activeCurrency = explicitCurrency
                    ?.takeIf { it in availableCurrencies }
                    ?: availableCurrencies.firstOrNull()
                    ?: Currency.TRY

                val distribution = if (assets.isNotEmpty()) {
                    AssetDisplayModelMapper.mapDistribution(assets, activeCurrency)
                } else null

                AssetDistributionUiState(
                    isLoading = false,
                    availableCurrencies = availableCurrencies,
                    selectedCurrency = activeCurrency,
                    distribution = distribution,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AssetDistributionUiState(isLoading = true),
    )

    fun selectCurrency(currency: Currency) {
        selectedCurrencyFlow.value = currency
    }

    fun setInitialCurrency(currencyCode: String?) {
        if (!currencyCode.isNullOrBlank()) {
            val matched = Currency.entries.find { it.code.equals(currencyCode.trim(), ignoreCase = true) }
            if (matched != null && selectedCurrencyFlow.value == null) {
                selectedCurrencyFlow.value = matched
            }
        }
    }

    fun retry() {
        retryTrigger.update { it + 1 }
    }
}
