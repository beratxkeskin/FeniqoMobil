package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.repository.AssetRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ObserveAssetsUseCase
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
class AssetDistributionViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun distribution_groupsByActiveCurrencyAndCalculatesProportions() = runTest {
        val repo = FakeAssetRepository()
        val viewModel = AssetDistributionViewModel(ObserveAssetsUseCase(repo))

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val assets = listOf(
            asset("1", "Gram Altın", AssetType.PRECIOUS_METALS, 15_000_000L, Currency.TRY),
            asset("2", "Hisse", AssetType.STOCKS, 7_500_000L, Currency.TRY),
            asset("3", "Nakit", AssetType.CASH, 2_500_000L, Currency.TRY),
            asset("4", "Dolar Nakit", AssetType.CASH, 200_000L, Currency.USD),
        )
        repo.assetsFlow.value = assets

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(Currency.TRY, Currency.USD), state.availableCurrencies)
        assertEquals(Currency.TRY, state.selectedCurrency)

        val dist = state.distribution
        assertNotNull(dist)
        assertEquals("250.000,00 ₺", dist!!.overallTotalFormatted)
        assertEquals(3, dist.assetCount)
        assertEquals(3, dist.items.size)
        assertEquals("%60", dist.items[0].percentageText)
        assertEquals("%30", dist.items[1].percentageText)
        assertEquals("%10", dist.items[2].percentageText)
    }

    @Test
    fun selectCurrency_switchesCurrencyWithoutMixingTotals() = runTest {
        val repo = FakeAssetRepository()
        val viewModel = AssetDistributionViewModel(ObserveAssetsUseCase(repo))

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val assets = listOf(
            asset("1", "Gram Altın", AssetType.PRECIOUS_METALS, 15_000_000L, Currency.TRY),
            asset("2", "Dolar Nakit", AssetType.CASH, 200_000L, Currency.USD),
        )
        repo.assetsFlow.value = assets

        viewModel.selectCurrency(Currency.USD)

        val state = viewModel.uiState.value
        assertEquals(Currency.USD, state.selectedCurrency)
        val dist = state.distribution
        assertNotNull(dist)
        assertEquals("2.000,00 $", dist!!.overallTotalFormatted)
        assertEquals(1, dist.assetCount)
        assertEquals(AssetType.CASH, dist.items.single().type)
        assertEquals("%100", dist.items.single().percentageText)
    }

    private fun asset(
        id: String,
        name: String,
        type: AssetType,
        amountMinor: Long,
        currency: Currency,
    ) = Asset(
        id = EntityId(id),
        ownerId = EntityId("owner"),
        workspaceId = null,
        name = name,
        type = type,
        currentValue = Money(amountMinor, currency),
        quantity = null,
        purchaseUnitPrice = null,
        trackingSymbol = null,
        autoTrack = false,
        createdAt = Instant.fromEpochMilliseconds(1L),
    )

    private class FakeAssetRepository : AssetRepository {
        val assetsFlow = MutableStateFlow<List<Asset>>(emptyList())
        override fun observeAssets(): Flow<List<Asset>> = assetsFlow
        override fun observeAsset(id: EntityId): Flow<Asset?> = emptyFlow()
        override suspend fun create(command: CreateAssetCommand): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("created"))
        override suspend fun update(command: UpdateAssetCommand): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }
}
