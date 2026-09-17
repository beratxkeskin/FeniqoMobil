package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.repository.AssetRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.*
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetFormViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun create_validatesThenEmitsSuccess() = runTest {
        val repository = FakeAssetRepository()
        val viewModel = viewModel(repository)
        viewModel.submit()
        assertEquals(AssetFormFieldError.NAME_REQUIRED, viewModel.uiState.value.errors.name)

        viewModel.updateInput { it.copy(nameInput = "Nakit", currentValueInput = "250") }
        viewModel.submit()
        assertEquals(25_000L, repository.created?.currentValue?.amountMinor)
        assertEquals(
            AssetFormUiEvent.MutationSuccess(FinanceUiMessage.ASSET_SAVED),
            viewModel.events.first(),
        )
    }

    @Test
    fun editLoad_preservesIdentityAndDeleteUsesLoadedId() = runTest {
        val repository = FakeAssetRepository()
        val existing = asset("asset-1")
        repository.asset.value = existing
        val viewModel = viewModel(repository)
        viewModel.loadForEdit(existing.id)
        assertEquals(AssetEditLoadState.Ready, viewModel.editLoadState.value)
        assertEquals(existing.id, viewModel.uiState.value.input.assetId)

        viewModel.requestDelete()
        assertTrue(viewModel.uiState.value.pendingDeleteConfirmation)
        viewModel.confirmDelete()
        assertEquals(existing.id, repository.deleted)
        assertEquals(
            AssetFormUiEvent.MutationSuccess(FinanceUiMessage.ASSET_DELETED),
            viewModel.events.first(),
        )
    }

    @Test
    fun updateInput_recalculatesCostPreviewAndAllowsCurrencyChange() = runTest {
        val repository = FakeAssetRepository()
        val viewModel = viewModel(repository)

        viewModel.updateInput {
            it.copy(
                nameInput = "Gram altın",
                currentValueInput = "150000",
                quantityInput = "30",
                purchaseUnitPriceInput = "4000",
                currency = Currency.TRY,
            )
        }

        assertEquals("120.000,00 ₺", viewModel.uiState.value.calculatedCostPreview)

        viewModel.updateInput { it.copy(currency = Currency.USD) }
        assertEquals(Currency.USD, viewModel.uiState.value.input.currency)
    }

    private fun viewModel(repository: FakeAssetRepository) = AssetFormViewModel(
        ObserveAssetUseCase(repository), CreateAssetUseCase(repository),
        UpdateAssetUseCase(repository), DeleteAssetUseCase(repository),
    )

    private class FakeAssetRepository : AssetRepository {
        val asset = MutableStateFlow<Asset?>(null)
        var created: CreateAssetCommand? = null
        var deleted: EntityId? = null
        override fun observeAssets(): Flow<List<Asset>> = emptyFlow()
        override fun observeAsset(id: EntityId): Flow<Asset?> = asset
        override suspend fun create(command: CreateAssetCommand): RepositoryResult<EntityId> {
            created = command
            return RepositoryResult.Success(EntityId("created"))
        }
        override suspend fun update(command: UpdateAssetCommand): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            deleted = id
            return RepositoryResult.Success(Unit)
        }
    }

    private fun asset(id: String) = Asset(
        EntityId(id), EntityId("owner"), null, "Nakit", AssetType.CASH,
        Money(10_000L, Currency.TRY), null, null, null, false,
        Instant.fromEpochMilliseconds(1L),
    )
}
