package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.usecase.NetWorthCalculationResult
import com.feniqo.mobile.domain.validation.AssetFinancialCalculator
import com.feniqo.mobile.domain.validation.CostCalculationResult
import com.feniqo.mobile.domain.validation.DifferenceCalculationResult
import com.feniqo.mobile.presentation.common.FinanceUiMessage

data class AssetsUiState(
    val isLoading: Boolean = true,
    val assets: List<AssetDisplayModel> = emptyList(),
    val netWorth: NetWorthDisplayModel? = null,
    val netWorthError: Boolean = false,
    val distributionSummary: AssetDistributionDisplayModel? = null,
    val observationError: FinanceUiMessage? = null,
    val isRefreshingPrices: Boolean = false,
    val priceRefreshError: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && assets.isEmpty()
}

data class CurrencyTotalDisplayItem(
    val currency: Currency,
    val total: Money,
    val assetCount: Int,
)

enum class NetWorthSourceSummary {
    MARKET_FRESH_ONLY,
    MARKET_AND_MANUAL,
    MARKET_STALE,
    MANUAL_ONLY,
}

data class NetWorthDisplayModel(
    val totals: List<Money>,
    val currencyTotals: List<CurrencyTotalDisplayItem>,
    val assetCount: Int,
    val hasMultipleCurrencies: Boolean,
    val primaryCurrency: Currency?,
    val primaryTotal: Money?,
    val sourceSummary: NetWorthSourceSummary,
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
    val currentValue: Money,
    val currency: Currency,
    val quantity: AssetQuantity?,
    val trackingSymbol: String?,
    val autoTrack: Boolean,
    val valueSource: AssetValueSource = AssetValueSource.MANUAL,
)

data class AssetDistributionItemDisplayModel(
    val type: AssetType,
    val total: Money,
    val percentageBps: Int,
    val assetCount: Int,
)

data class AssetDistributionDisplayModel(
    val currency: Currency,
    val overallTotal: Money,
    val assetCount: Int,
    val items: List<AssetDistributionItemDisplayModel>,
)

data class AssetDistributionUiState(
    val isLoading: Boolean = true,
    val availableCurrencies: List<Currency> = emptyList(),
    val selectedCurrency: Currency = Currency.TRY,
    val distribution: AssetDistributionDisplayModel? = null,
    val observationError: FinanceUiMessage? = null,
)

data class AssetDetailDisplayModel(
    val id: EntityId,
    val name: String,
    val type: AssetType,
    val currentValue: Money,
    val currency: Currency,
    val quantity: AssetQuantity?,
    val purchaseUnitPrice: Money?,
    val calculatedCost: Money?,
    val difference: MoneyDelta?,
    val differencePercentageBps: Int?,
    val hasCalculatedCost: Boolean,
    val valueSource: AssetValueSource = AssetValueSource.MANUAL,
    val isPriceVerificationFailed: Boolean,
    val canRetryPrice: Boolean,
    val trackingSymbol: String?,
    val autoTrack: Boolean,
)

data class AssetDetailUiState(
    val isLoading: Boolean = true,
    val asset: AssetDetailDisplayModel? = null,
    val isNotFound: Boolean = false,
    val isDeleting: Boolean = false,
    val pendingDeleteConfirmation: Boolean = false,
    val isRefreshingPrice: Boolean = false,
    val priceRefreshError: Boolean = false,
    val error: FinanceUiMessage? = null,
)

object AssetDisplayModelMapper {
    fun map(
        assets: List<Asset>,
        valueSources: Map<EntityId, AssetValueSource> = emptyMap(),
    ): List<AssetDisplayModel> =
        assets
            .map { mapItem(it, valueSources[it.id] ?: AssetValueSource.MANUAL) }
            .sortedWith(
                compareBy<AssetDisplayModel> { it.type.ordinal }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.id.value },
            )

    fun mapItem(
        asset: Asset,
        valueSource: AssetValueSource = AssetValueSource.MANUAL,
    ): AssetDisplayModel =
        AssetDisplayModel(
            id = asset.id,
            name = asset.name,
            type = asset.type,
            currentValue = asset.currentValue,
            currency = asset.currentValue.currency,
            quantity = asset.quantity,
            trackingSymbol = asset.trackingSymbol,
            autoTrack = asset.autoTrack,
            valueSource = valueSource,
        )

    fun mapDetail(
        asset: Asset,
        valueSource: AssetValueSource = AssetValueSource.MANUAL,
        isVerificationFailed: Boolean = false,
    ): AssetDetailDisplayModel {
        var calculatedCost: Money? = null
        var difference: MoneyDelta? = null
        var differencePercentageBps: Int? = null
        var hasCost = false

        val quantity = asset.quantity
        val purchasePrice = asset.purchaseUnitPrice

        if (quantity != null && purchasePrice != null && purchasePrice.currency == asset.currentValue.currency) {
            val costResult = AssetFinancialCalculator.calculateCost(quantity, purchasePrice)
            if (costResult is CostCalculationResult.Success) {
                hasCost = true
                calculatedCost = costResult.cost
                val diffResult = AssetFinancialCalculator.calculateDifference(asset.currentValue, costResult.cost)
                if (diffResult is DifferenceCalculationResult.Success) {
                    difference = diffResult.difference
                    differencePercentageBps = diffResult.percentageBps
                }
            }
        }

        return AssetDetailDisplayModel(
            id = asset.id,
            name = asset.name,
            type = asset.type,
            currentValue = asset.currentValue,
            currency = asset.currentValue.currency,
            quantity = asset.quantity,
            purchaseUnitPrice = asset.purchaseUnitPrice,
            calculatedCost = calculatedCost,
            difference = difference,
            differencePercentageBps = differencePercentageBps,
            hasCalculatedCost = hasCost,
            valueSource = valueSource,
            isPriceVerificationFailed = isVerificationFailed,
            canRetryPrice = asset.autoTrack && !asset.trackingSymbol.isNullOrBlank(),
            trackingSymbol = asset.trackingSymbol,
            autoTrack = asset.autoTrack,
        )
    }

    fun mapDistribution(
        assets: List<Asset>,
        currency: Currency,
    ): AssetDistributionDisplayModel {
        val distribution = AssetFinancialCalculator.calculateDistribution(assets, currency)
        val items =
            distribution.items.map { item ->
                AssetDistributionItemDisplayModel(
                    type = item.type,
                    total = item.total,
                    percentageBps = item.percentageBps,
                    assetCount = item.assetCount,
                )
            }
        return AssetDistributionDisplayModel(
            currency = currency,
            overallTotal = distribution.overallTotal,
            assetCount = distribution.assetCount,
            items = items,
        )
    }
}

object NetWorthDisplayModelMapper {
    fun map(
        result: NetWorthCalculationResult,
        assets: List<Asset> = emptyList(),
        valueSources: Map<EntityId, AssetValueSource> = emptyMap(),
    ): NetWorthDisplayModel? =
        when (result) {
            NetWorthCalculationResult.InvalidAssetValue,
            NetWorthCalculationResult.Overflow,
            -> null
            is NetWorthCalculationResult.Success -> {
                val currencyTotals =
                    result.totals.map { money ->
                        val count = assets.count { it.currentValue.currency == money.currency }
                        CurrencyTotalDisplayItem(
                            currency = money.currency,
                            total = money,
                            assetCount = count,
                        )
                    }
                val hasMultiple = result.totals.size > 1
                val primaryTotal = result.totals.firstOrNull()

                val hasMarketFresh = valueSources.values.any { it == AssetValueSource.MARKET_FRESH }
                val hasMarketStale = valueSources.values.any { it == AssetValueSource.MARKET_STALE }
                val hasManual = valueSources.values.any { it == AssetValueSource.MANUAL } || assets.any { !it.autoTrack }

                val sourceSummary =
                    when {
                        hasMarketFresh && !hasMarketStale && !hasManual -> NetWorthSourceSummary.MARKET_FRESH_ONLY
                        hasMarketFresh && hasManual -> NetWorthSourceSummary.MARKET_AND_MANUAL
                        hasMarketStale -> NetWorthSourceSummary.MARKET_STALE
                        else -> NetWorthSourceSummary.MANUAL_ONLY
                    }

                NetWorthDisplayModel(
                    totals = result.totals,
                    currencyTotals = currencyTotals,
                    assetCount = result.assetCount,
                    hasMultipleCurrencies = hasMultiple,
                    primaryCurrency = primaryTotal?.currency,
                    primaryTotal = primaryTotal,
                    sourceSummary = sourceSummary,
                )
            }
        }
}
