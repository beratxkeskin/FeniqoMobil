package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.MoneyFormatter
import com.feniqo.mobile.domain.usecase.NetWorthCalculationResult

data class AssetsUiState(
    val isLoading: Boolean = true,
    val assets: List<AssetDisplayModel> = emptyList(),
    val netWorth: NetWorthDisplayModel? = null,
    val netWorthError: Boolean = false,
    val observationError: FinanceUiMessage? = null,
    val isRefreshingPrices: Boolean = false,
    val priceRefreshError: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && assets.isEmpty()
}

data class NetWorthDisplayModel(
    val totalsFormatted: List<String>,
    val assetCount: Int,
    val hasMultipleCurrencies: Boolean,
)

sealed interface AssetsIntent {
    data object Retry : AssetsIntent
    data object RefreshPrices : AssetsIntent
}

enum class AssetValueSource {
    MARKET_FRESH,
    MARKET_STALE,
    MANUAL,
}

data class AssetDisplayModel(
    val id: EntityId,
    val name: String,
    val type: AssetType,
    val typeLabel: String,
    val currentValueFormatted: String,
    val currency: Currency,
    val quantityFormatted: String?,
    val trackingSymbol: String?,
    val autoTrack: Boolean,
    val valueSource: AssetValueSource = AssetValueSource.MANUAL,
)

object AssetDisplayModelMapper {
    fun map(
        assets: List<Asset>,
        valueSources: Map<EntityId, AssetValueSource> = emptyMap(),
    ): List<AssetDisplayModel> = assets
        .map { mapItem(it, valueSources[it.id] ?: AssetValueSource.MANUAL) }
        .sortedWith(
            compareBy<AssetDisplayModel> { it.type.ordinal }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.id.value },
        )

    fun mapItem(
        asset: Asset,
        valueSource: AssetValueSource = AssetValueSource.MANUAL,
    ): AssetDisplayModel = AssetDisplayModel(
        id = asset.id,
        name = asset.name,
        type = asset.type,
        typeLabel = asset.type.toDisplayLabel(),
        currentValueFormatted = MoneyFormatter.format(asset.currentValue),
        currency = asset.currentValue.currency,
        quantityFormatted = asset.quantity?.toDisplayText(),
        trackingSymbol = asset.trackingSymbol,
        autoTrack = asset.autoTrack,
        valueSource = valueSource,
    )
}

object NetWorthDisplayModelMapper {
    fun map(result: NetWorthCalculationResult): NetWorthDisplayModel? = when (result) {
        NetWorthCalculationResult.InvalidAssetValue,
        NetWorthCalculationResult.Overflow -> null
        is NetWorthCalculationResult.Success -> NetWorthDisplayModel(
            totalsFormatted = result.totals.map(MoneyFormatter::format),
            assetCount = result.assetCount,
            hasMultipleCurrencies = result.totals.size > 1,
        )
    }
}

fun AssetType.toDisplayLabel(): String = when (this) {
    AssetType.CASH -> "Nakit"
    AssetType.CRYPTO -> "Kripto"
    AssetType.STOCKS -> "Hisse Senedi"
    AssetType.REAL_ESTATE -> "Gayrimenkul"
    AssetType.PRECIOUS_METALS -> "Değerli Metal"
    AssetType.OTHER -> "Diğer"
}

private fun AssetQuantity.toDisplayText(): String {
    if (scale == 0) return unscaledValue.toString()
    val digits = unscaledValue.toString().padStart(scale + 1, '0')
    val fractional = digits.takeLast(scale).trimEnd('0')
    return if (fractional.isEmpty()) digits.dropLast(scale) else "${digits.dropLast(scale)},$fractional"
}
