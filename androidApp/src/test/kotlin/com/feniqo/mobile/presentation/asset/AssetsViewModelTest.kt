package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.repository.AssetRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.MarketPriceRepository
import com.feniqo.mobile.domain.usecase.ObserveAssetsUseCase
import com.feniqo.mobile.domain.usecase.CalculateNetWorthUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observation_mapsRoomFlowAndEmptyState() = runTest {
        val repository = FakeAssetRepository()
        val viewModel = AssetsViewModel(ObserveAssetsUseCase(repository), CalculateNetWorthUseCase(), FakeMarketPriceRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertTrue(viewModel.uiState.value.isEmpty)

        repository.assets.value = listOf(asset("asset-1"))
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertEquals("asset-1", state.assets.single().id.value)
        assertEquals(listOf("100,00 ₺"), state.netWorth?.totalsFormatted)
        assertEquals(1, state.netWorth?.assetCount)
        assertNull(state.observationError)
    }

    @Test
    fun retry_recoversFromObservationFailure() = runTest {
        val repository = FakeAssetRepository().apply { failure = IllegalStateException("room") }
        val viewModel = AssetsViewModel(ObserveAssetsUseCase(repository), CalculateNetWorthUseCase(), FakeMarketPriceRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertEquals(FinanceUiMessage.GENERIC_ERROR, viewModel.uiState.value.observationError)

        repository.failure = null
        repository.assets.value = listOf(asset("asset-2"))
        viewModel.onIntent(AssetsIntent.Retry)
        assertEquals("asset-2", viewModel.uiState.value.assets.single().id.value)
        assertTrue(repository.observeCount.get() >= 2)
    }

    @Test
    fun freshMarketQuote_replacesManualValueAndNetWorth() = runTest {
        val assetRepository = FakeAssetRepository()
        val marketRepository = FakeMarketPriceRepository()
        val viewModel = AssetsViewModel(
            ObserveAssetsUseCase(assetRepository),
            CalculateNetWorthUseCase(),
            marketRepository,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assetRepository.assets.value = listOf(trackedAsset())
        marketRepository.quote.value = MarketPriceQuote(
            assetType = AssetType.STOCKS,
            symbol = "AAPL",
            price = ScaledMarketPrice(2_500L, 2, Currency.USD),
            observedAt = Instant.fromEpochMilliseconds(1L),
            fetchedAt = Instant.fromEpochMilliseconds(2L),
            source = "test",
            availability = MarketPriceAvailability.FRESH,
        )

        val state = viewModel.uiState.value
        assertEquals("50,00 $", state.assets.single().currentValueFormatted)
        assertEquals(AssetValueSource.MARKET_FRESH, state.assets.single().valueSource)
        assertEquals(listOf("50,00 $"), state.netWorth?.totalsFormatted)
    }

    @Test
    fun refreshIntent_callsRemoteOnlyForTrackedAssets() = runTest {
        val assetRepository = FakeAssetRepository()
        val marketRepository = FakeMarketPriceRepository()
        val viewModel = AssetsViewModel(
            ObserveAssetsUseCase(assetRepository),
            CalculateNetWorthUseCase(),
            marketRepository,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assetRepository.assets.value = listOf(asset("cash"), trackedAsset())

        viewModel.onIntent(AssetsIntent.RefreshPrices)

        assertEquals(listOf("AAPL"), marketRepository.lastRequests.map { it.symbol })
        assertFalse(viewModel.uiState.value.isRefreshingPrices)
    }

    private class FakeAssetRepository : AssetRepository {
        val assets = MutableStateFlow<List<Asset>>(emptyList())
        val observeCount = AtomicInteger(0)
        var failure: Throwable? = null
        override fun observeAssets(): Flow<List<Asset>> = flow {
            observeCount.incrementAndGet()
            failure?.let { throw it }
            assets.collect { emit(it) }
        }
        override fun observeAsset(id: EntityId): Flow<Asset?> = emptyFlow()
        override suspend fun create(command: CreateAssetCommand): RepositoryResult<EntityId> = error("Not needed")
        override suspend fun update(command: UpdateAssetCommand): RepositoryResult<Unit> = error("Not needed")
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = error("Not needed")
    }

    private class FakeMarketPriceRepository : MarketPriceRepository {
        val quote = MutableStateFlow<MarketPriceQuote?>(null)
        var lastRequests = emptyList<MarketPriceRequest>()
        override fun observe(request: MarketPriceRequest): Flow<MarketPriceQuote?> = quote
        override suspend fun refresh(requests: List<MarketPriceRequest>): RepositoryResult<List<MarketPriceQuote>> {
            lastRequests = requests
            return RepositoryResult.Success(emptyList())
        }
    }

    private fun asset(id: String) = Asset(
        id = EntityId(id), ownerId = EntityId("owner-1"), workspaceId = null, name = "Nakit",
        type = AssetType.CASH, currentValue = Money(10_000L, Currency.TRY), quantity = null,
        purchaseUnitPrice = null, trackingSymbol = null, autoTrack = false,
        createdAt = Instant.fromEpochMilliseconds(1L),
    )

    private fun trackedAsset() = Asset(
        id = EntityId("stock"), ownerId = EntityId("owner-1"), workspaceId = null, name = "Apple",
        type = AssetType.STOCKS, currentValue = Money(1_000L, Currency.USD),
        quantity = AssetQuantity(2L, 0), purchaseUnitPrice = null, trackingSymbol = "AAPL",
        autoTrack = true, createdAt = Instant.fromEpochMilliseconds(1L),
    )
}
