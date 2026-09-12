package com.feniqo.mobile.presentation.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.usecase.ObserveAssetsUseCase
import com.feniqo.mobile.domain.usecase.CalculateNetWorthUseCase
import com.feniqo.mobile.domain.usecase.NetWorthCalculationResult
import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MarketPriceAvailability
import com.feniqo.mobile.domain.model.MarketPriceQuote
import com.feniqo.mobile.domain.model.MarketPriceRequest
import com.feniqo.mobile.domain.model.MarketValueCalculationResult
import com.feniqo.mobile.domain.repository.MarketPriceRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.validation.MarketValueCalculator
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val observeAssetsUseCase: ObserveAssetsUseCase,
    private val calculateNetWorthUseCase: CalculateNetWorthUseCase,
    private val marketPriceRepository: MarketPriceRepository,
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0L)
    private val latestAssets = MutableStateFlow<List<Asset>>(emptyList())
    private val refreshState = MutableStateFlow(PriceRefreshState())

    private data class PriceRefreshState(
        val isRefreshing: Boolean = false,
        val hasError: Boolean = false,
    )

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(
            val assets: List<AssetDisplayModel>,
            val netWorth: com.feniqo.mobile.presentation.asset.NetWorthDisplayModel?,
            val netWorthError: Boolean,
        ) : ObservationResult
        data object Failure : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observation: Flow<ObservationResult> = retryTrigger.flatMapLatest {
        observeAssetsUseCase()
            .map { assets -> latestAssets.value = assets; assets }
            .flatMapLatest(::observeMarketAdjustedAssets)
            .map { resolved ->
                val calculation = calculateNetWorthUseCase(resolved.assets)
                ObservationResult.Success(
                    assets = AssetDisplayModelMapper.map(resolved.assets, resolved.valueSources),
                    netWorth = NetWorthDisplayModelMapper.map(calculation),
                    netWorthError = calculation !is NetWorthCalculationResult.Success,
                ) as ObservationResult
            }
            .onStart { emit(ObservationResult.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(ObservationResult.Failure)
            }
    }

    val uiState: StateFlow<AssetsUiState> = combine(observation, refreshState) { result, refresh ->
            when (result) {
                ObservationResult.Loading -> AssetsUiState(isLoading = true)
                ObservationResult.Failure -> AssetsUiState(
                    isLoading = false,
                    observationError = FinanceUiMessage.GENERIC_ERROR,
                )
                is ObservationResult.Success -> AssetsUiState(
                    isLoading = false,
                    assets = result.assets,
                    netWorth = result.netWorth,
                    netWorthError = result.netWorthError,
                    isRefreshingPrices = refresh.isRefreshing,
                    priceRefreshError = refresh.hasError,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AssetsUiState(),
        )

    fun onIntent(intent: AssetsIntent) {
        when (intent) {
            AssetsIntent.Retry -> retryTrigger.update { it + 1 }
            AssetsIntent.RefreshPrices -> refreshPrices()
        }
    }

    private data class ResolvedAssets(
        val assets: List<Asset>,
        val valueSources: Map<EntityId, AssetValueSource>,
    )

    private fun observeMarketAdjustedAssets(assets: List<Asset>): Flow<ResolvedAssets> {
        val tracked = assets.mapNotNull { asset -> asset.toMarketRequest()?.let { asset to it } }
        if (tracked.isEmpty()) return flowOf(ResolvedAssets(assets, emptyMap()))
        val quoteFlows = tracked.map { (asset, request) ->
            marketPriceRepository.observe(request).map { quote -> asset.id to quote }
        }
        return combine(quoteFlows) { entries ->
            val quotes = entries.toMap()
            val sources = mutableMapOf<EntityId, AssetValueSource>()
            val adjusted = assets.map { asset ->
                val quote = quotes[asset.id]
                val quantity = asset.quantity
                val price = quote?.price
                val calculated = if (quantity != null && price != null) {
                    MarketValueCalculator.calculate(quantity, price)
                } else null
                if (calculated is MarketValueCalculationResult.Success) {
                    sources[asset.id] = when (quote?.availability) {
                        MarketPriceAvailability.FRESH -> AssetValueSource.MARKET_FRESH
                        MarketPriceAvailability.STALE -> AssetValueSource.MARKET_STALE
                        else -> AssetValueSource.MANUAL
                    }
                    if (sources[asset.id] != AssetValueSource.MANUAL) asset.copy(currentValue = calculated.value) else asset
                } else {
                    sources[asset.id] = AssetValueSource.MANUAL
                    asset
                }
            }
            ResolvedAssets(adjusted, sources)
        }
    }

    private fun refreshPrices() {
        if (refreshState.value.isRefreshing) return
        val requests = latestAssets.value.mapNotNull { it.toMarketRequest() }.distinct()
        if (requests.isEmpty()) return
        viewModelScope.launch {
            refreshState.value = PriceRefreshState(isRefreshing = true)
            val result = marketPriceRepository.refresh(requests)
            refreshState.value = PriceRefreshState(
                isRefreshing = false,
                hasError = result is RepositoryResult.Failure,
            )
        }
    }

    private fun Asset.toMarketRequest(): MarketPriceRequest? {
        val symbol = trackingSymbol ?: return null
        if (!autoTrack || quantity == null) return null
        return MarketPriceRequest(type, symbol, currentValue.currency)
    }
}
