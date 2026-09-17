package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.repository.AssetRepository
import com.feniqo.mobile.domain.repository.MarketPriceRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.DeleteAssetUseCase
import com.feniqo.mobile.domain.usecase.ObserveAssetUseCase
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetDetailViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadAsset_calculatesCostAndDifference_whenFieldsPresent() = runTest {
        val assetRepo = FakeAssetRepository()
        val marketRepo = FakeMarketPriceRepository()
        val viewModel = AssetDetailViewModel(
            ObserveAssetUseCase(assetRepo),
            DeleteAssetUseCase(assetRepo),
            marketRepo,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val asset = Asset(
            id = EntityId("a-1"),
            ownerId = EntityId("owner"),
            workspaceId = null,
            name = "Gram altın",
            type = AssetType.PRECIOUS_METALS,
            currentValue = Money(15_000_000L, Currency.TRY),
            quantity = AssetQuantity(30L, 0),
            purchaseUnitPrice = Money(400_000L, Currency.TRY),
            trackingSymbol = null,
            autoTrack = false,
            createdAt = Instant.fromEpochMilliseconds(1L),
        )
        assetRepo.assetFlow.value = asset
        viewModel.loadAsset(asset.id)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.asset)
        val detail = state.asset!!
        assertEquals("Gram altın", detail.name)
        assertEquals("150.000,00 ₺", detail.currentValueFormatted)
        assertTrue(detail.hasCalculatedCost)
        assertEquals("120.000,00 ₺", detail.calculatedCostFormatted)
        assertEquals("+ 30.000,00 ₺", detail.differenceFormatted)
        assertEquals("(+%25)", detail.differencePercentageText)
        assertEquals(true, detail.isDifferencePositive)
        assertFalse(detail.isPriceVerificationFailed)
    }

    @Test
    fun loadAsset_missingOptionalFields_leavesCostNullWithoutFakeZeros() = runTest {
        val assetRepo = FakeAssetRepository()
        val marketRepo = FakeMarketPriceRepository()
        val viewModel = AssetDetailViewModel(
            ObserveAssetUseCase(assetRepo),
            DeleteAssetUseCase(assetRepo),
            marketRepo,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val asset = Asset(
            id = EntityId("a-2"),
            ownerId = EntityId("owner"),
            workspaceId = null,
            name = "Nakit hesap",
            type = AssetType.CASH,
            currentValue = Money(25_000_000L, Currency.TRY),
            quantity = null,
            purchaseUnitPrice = null,
            trackingSymbol = null,
            autoTrack = false,
            createdAt = Instant.fromEpochMilliseconds(1L),
        )
        assetRepo.assetFlow.value = asset
        viewModel.loadAsset(asset.id)

        val detail = viewModel.uiState.value.asset
        assertNotNull(detail)
        assertFalse(detail!!.hasCalculatedCost)
        assertNull(detail.calculatedCostFormatted)
        assertNull(detail.differenceFormatted)
    }

    @Test
    fun marketQuoteUnavailable_triggersVerificationFailedState() = runTest {
        val assetRepo = FakeAssetRepository()
        val marketRepo = FakeMarketPriceRepository()
        val viewModel = AssetDetailViewModel(
            ObserveAssetUseCase(assetRepo),
            DeleteAssetUseCase(assetRepo),
            marketRepo,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val tracked = Asset(
            id = EntityId("btc-1"),
            ownerId = EntityId("owner"),
            workspaceId = null,
            name = "Bitcoin",
            type = AssetType.CRYPTO,
            currentValue = Money(5_000_000L, Currency.TRY),
            quantity = AssetQuantity(1L, 2),
            purchaseUnitPrice = null,
            trackingSymbol = "BTC",
            autoTrack = true,
            createdAt = Instant.fromEpochMilliseconds(1L),
        )
        assetRepo.assetFlow.value = tracked
        marketRepo.quoteFlow.value = null // Quote alınamadı
        viewModel.loadAsset(tracked.id)

        val detail = viewModel.uiState.value.asset
        assertNotNull(detail)
        assertTrue(detail!!.isPriceVerificationFailed)
        assertTrue(detail.canRetryPrice)
    }

    @Test
    fun deleteFlow_callsRepositoryAndTriggersCallback() = runTest {
        val assetRepo = FakeAssetRepository()
        val marketRepo = FakeMarketPriceRepository()
        val viewModel = AssetDetailViewModel(
            ObserveAssetUseCase(assetRepo),
            DeleteAssetUseCase(assetRepo),
            marketRepo,
        )

        val asset = Asset(
            id = EntityId("delete-me"),
            ownerId = EntityId("owner"),
            workspaceId = null,
            name = "Silinecek",
            type = AssetType.OTHER,
            currentValue = Money(100L, Currency.TRY),
            quantity = null,
            purchaseUnitPrice = null,
            trackingSymbol = null,
            autoTrack = false,
            createdAt = Instant.fromEpochMilliseconds(1L),
        )
        assetRepo.assetFlow.value = asset
        viewModel.loadAsset(asset.id)

        var deletedCalled = false
        viewModel.confirmDelete { deletedCalled = true }

        assertEquals(EntityId("delete-me"), assetRepo.deletedId)
        assertTrue(deletedCalled)
    }

    private class FakeAssetRepository : AssetRepository {
        val assetFlow = MutableStateFlow<Asset?>(null)
        var deletedId: EntityId? = null
        override fun observeAssets(): Flow<List<Asset>> = emptyFlow()
        override fun observeAsset(id: EntityId): Flow<Asset?> = assetFlow
        override suspend fun create(command: CreateAssetCommand): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("created"))
        override suspend fun update(command: UpdateAssetCommand): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            deletedId = id
            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeMarketPriceRepository : MarketPriceRepository {
        val quoteFlow = MutableStateFlow<MarketPriceQuote?>(null)
        var lastRefreshRequests = emptyList<MarketPriceRequest>()
        override fun observe(request: MarketPriceRequest): Flow<MarketPriceQuote?> = quoteFlow
        override suspend fun refresh(requests: List<MarketPriceRequest>): RepositoryResult<List<MarketPriceQuote>> {
            lastRefreshRequests = requests
            return RepositoryResult.Success(emptyList())
        }
    }
}
