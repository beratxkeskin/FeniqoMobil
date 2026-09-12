package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.CreateAssetCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateAssetCommand
import com.feniqo.mobile.domain.repository.AssetRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.Flow

class ObserveAssetsUseCase(private val repository: AssetRepository) {
    operator fun invoke(): Flow<List<Asset>> = repository.observeAssets()
}

class ObserveAssetUseCase(private val repository: AssetRepository) {
    operator fun invoke(id: EntityId): Flow<Asset?> = repository.observeAsset(id)
}

class CreateAssetUseCase(private val repository: AssetRepository) {
    suspend operator fun invoke(command: CreateAssetCommand): RepositoryResult<EntityId> = repository.create(command)
}

class UpdateAssetUseCase(private val repository: AssetRepository) {
    suspend operator fun invoke(command: UpdateAssetCommand): RepositoryResult<Unit> = repository.update(command)
}

class DeleteAssetUseCase(private val repository: AssetRepository) {
    suspend operator fun invoke(id: EntityId): RepositoryResult<Unit> = repository.softDelete(id)
}

sealed interface NetWorthCalculationResult {
    data class Success(
        val totals: List<Money>,
        val assetCount: Int,
    ) : NetWorthCalculationResult

    data object InvalidAssetValue : NetWorthCalculationResult
    data object Overflow : NetWorthCalculationResult
}

/** Kur dönüşümü yapmadan aktif varlık değerlerini para birimi bazında toplar. */
class CalculateNetWorthUseCase {
    operator fun invoke(assets: List<Asset>): NetWorthCalculationResult {
        val totals = linkedMapOf<com.feniqo.mobile.domain.model.Currency, Long>()
        for (asset in assets) {
            val amount = asset.currentValue.amountMinor
            if (amount < 0L) return NetWorthCalculationResult.InvalidAssetValue
            val current = totals[asset.currentValue.currency] ?: 0L
            if (current > Money.MAX_AMOUNT_MINOR - amount) return NetWorthCalculationResult.Overflow
            totals[asset.currentValue.currency] = current + amount
        }
        return NetWorthCalculationResult.Success(
            totals = totals.entries
                .sortedBy { it.key.ordinal }
                .map { Money(it.value, it.key) },
            assetCount = assets.size,
        )
    }
}
