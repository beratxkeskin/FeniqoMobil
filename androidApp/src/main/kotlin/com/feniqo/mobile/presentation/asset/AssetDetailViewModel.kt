package com.feniqo.mobile.presentation.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MarketPriceAvailability
import com.feniqo.mobile.domain.model.MarketPriceQuote
import com.feniqo.mobile.domain.model.MarketPriceRequest
import com.feniqo.mobile.domain.model.MarketValueCalculationResult
import com.feniqo.mobile.domain.repository.MarketPriceRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.DeleteAssetUseCase
import com.feniqo.mobile.domain.usecase.ObserveAssetUseCase
import com.feniqo.mobile.domain.validation.MarketValueCalculator
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val observeAssetUseCase: ObserveAssetUseCase,
    private val deleteAssetUseCase: DeleteAssetUseCase,
    private val marketPriceRepository: MarketPriceRepository,
) : ViewModel() {

    private val currentAssetId = MutableStateFlow<EntityId?>(null)
    private val refreshState = MutableStateFlow(PriceRefreshState())
    private var deleteJob: Job? = null
    private var rawAsset: Asset? = null

    private data class PriceRefreshState(
        val isRefreshing: Boolean = false,
        val hasError: Boolean = false,
    )

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(
            val asset: Asset,
            val valueSource: AssetValueSource,
            val isVerificationFailed: Boolean,
        ) : ObservationResult
        data object NotFound : ObservationResult
        data object Failure : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observation: Flow<ObservationResult> = currentAssetId.flatMapLatest { id ->
        if (id == null) {
            flowOf(ObservationResult.Loading)
        } else {
            observeAssetUseCase(id).flatMapLatest { asset ->
                if (asset == null) {
                    flowOf(ObservationResult.NotFound)
                } else {
                    rawAsset = asset
                    val request = asset.toMarketRequest()
                    if (request == null) {
                        flowOf(
                            ObservationResult.Success(
                                asset = asset,
                                valueSource = AssetValueSource.MANUAL,
                                isVerificationFailed = false,
                            ),
                        )
                    } else {
                        marketPriceRepository.observe(request).map { quote ->
                            resolveMarketAdjustment(asset, quote)
                        }
                    }
                }
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(ObservationResult.Failure)
            }
        }
    }

    val uiState: StateFlow<AssetDetailUiState> = combine(
        observation,
        refreshState,
    ) { result, refresh ->
        when (result) {
            ObservationResult.Loading -> AssetDetailUiState(isLoading = true)
            ObservationResult.NotFound -> AssetDetailUiState(
                isLoading = false,
                isNotFound = true,
            )
            ObservationResult.Failure -> AssetDetailUiState(
                isLoading = false,
                error = FinanceUiMessage.GENERIC_ERROR,
            )
            is ObservationResult.Success -> {
                val detailModel = AssetDisplayModelMapper.mapDetail(
                    asset = result.asset,
                    valueSource = result.valueSource,
                    isVerificationFailed = result.isVerificationFailed || refresh.hasError,
                )
                AssetDetailUiState(
                    isLoading = false,
                    asset = detailModel,
                    isRefreshingPrice = refresh.isRefreshing,
                    priceRefreshError = refresh.hasError,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AssetDetailUiState(isLoading = true),
    )

    fun loadAsset(id: EntityId) {
        currentAssetId.value = id
    }

    fun requestDelete() {
        // UI'da silme onay sheet'ini açar
        _pendingDelete.value = true
    }

    fun dismissDelete() {
        _pendingDelete.value = false
    }

    private val _pendingDelete = MutableStateFlow(false)
    val pendingDelete: StateFlow<Boolean> = _pendingDelete.asStateFlow()

    fun confirmDelete(onSuccess: () -> Unit) {
        val id = currentAssetId.value ?: return
        if (deleteJob != null) return
        deleteJob = viewModelScope.launch {
            try {
                val result = deleteAssetUseCase(id)
                if (result is RepositoryResult.Success) {
                    _pendingDelete.value = false
                    onSuccess()
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                deleteJob = null
            }
        }
    }

    fun retryPriceRefresh() {
        if (refreshState.value.isRefreshing) return
        val asset = rawAsset ?: return
        val request = asset.toMarketRequest() ?: return
        viewModelScope.launch {
            refreshState.value = PriceRefreshState(isRefreshing = true)
            val result = marketPriceRepository.refresh(listOf(request))
            refreshState.value = PriceRefreshState(
                isRefreshing = false,
                hasError = result is RepositoryResult.Failure,
            )
        }
    }

    private fun resolveMarketAdjustment(asset: Asset, quote: MarketPriceQuote?): ObservationResult.Success {
        val quantity = asset.quantity
        val price = quote?.price
        val calculated = if (quantity != null && price != null) {
            MarketValueCalculator.calculate(quantity, price)
        } else null

        return if (calculated is MarketValueCalculationResult.Success) {
            val source = when (quote?.availability) {
                MarketPriceAvailability.FRESH -> AssetValueSource.MARKET_FRESH
                MarketPriceAvailability.STALE -> AssetValueSource.MARKET_STALE
                else -> AssetValueSource.MANUAL
            }
            val adjustedAsset = if (source != AssetValueSource.MANUAL) {
                asset.copy(currentValue = calculated.value)
            } else asset

            ObservationResult.Success(
                asset = adjustedAsset,
                valueSource = source,
                isVerificationFailed = source == AssetValueSource.MARKET_STALE,
            )
        } else {
            // Fiyat alınamadı veya hesaplanamadı -> son kayıtlı manuel değer korunur ve 09 durumu gösterilir
            ObservationResult.Success(
                asset = asset,
                valueSource = AssetValueSource.MANUAL,
                isVerificationFailed = quote == null || quote.availability == MarketPriceAvailability.UNAVAILABLE,
            )
        }
    }

    private fun Asset.toMarketRequest(): MarketPriceRequest? {
        val symbol = trackingSymbol ?: return null
        if (!autoTrack || quantity == null) return null
        return MarketPriceRequest(type, symbol, currentValue.currency)
    }
}
