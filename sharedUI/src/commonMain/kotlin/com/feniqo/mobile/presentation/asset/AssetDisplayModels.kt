package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.usecase.NetWorthCalculationResult
import com.feniqo.mobile.domain.validation.AssetFinancialCalculator
import com.feniqo.mobile.domain.validation.CostCalculationResult
import com.feniqo.mobile.domain.validation.DifferenceCalculationResult
import com.feniqo.mobile.domain.validation.TypeDistributionItem
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.MoneyFormatter

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
    val formattedTotal: String,
    val assetCount: Int,
)

data class NetWorthDisplayModel(
    val totalsFormatted: List<String>,
    val currencyTotals: List<CurrencyTotalDisplayItem>,
    val assetCount: Int,
    val hasMultipleCurrencies: Boolean,
    val primaryCurrency: Currency?,
    val primaryTotalFormatted: String?,
    val sourceSummary: String,
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

fun AssetValueSource.toDisplayLabel(): String = when (this) {
    AssetValueSource.MARKET_FRESH -> "Güncel piyasa"
    AssetValueSource.MARKET_STALE -> "Eski piyasa"
    AssetValueSource.MANUAL -> "Manuel"
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

data class AssetDistributionItemDisplayModel(
    val type: AssetType,
    val typeLabel: String,
    val formattedTotal: String,
    val percentageText: String, // "%60"
    val percentageBps: Int, // 6000
    val assetCount: Int,
)

data class AssetDistributionDisplayModel(
    val currency: Currency,
    val overallTotalFormatted: String,
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
    val typeLabel: String,
    val currentValueFormatted: String,
    val currency: Currency,
    val quantityFormatted: String?,
    val purchaseUnitPriceFormatted: String?,
    val calculatedCostFormatted: String?,
    val differenceFormatted: String?,
    val differencePercentageText: String?, // "+%25" or "-%8" or "%0"
    val isDifferencePositive: Boolean?, // true: green, false: red, null: neutral
    val isDifferenceZero: Boolean,
    val hasCalculatedCost: Boolean,
    val valueSource: AssetValueSource = AssetValueSource.MANUAL,
    val valueSourceLabel: String,
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

    fun mapDetail(
        asset: Asset,
        valueSource: AssetValueSource = AssetValueSource.MANUAL,
        isVerificationFailed: Boolean = false,
    ): AssetDetailDisplayModel {
        var calculatedCostFormatted: String? = null
        var differenceFormatted: String? = null
        var differencePercentageText: String? = null
        var isDiffPositive: Boolean? = null
        var isDiffZero = false
        var hasCost = false

        val quantity = asset.quantity
        val purchasePrice = asset.purchaseUnitPrice

        if (quantity != null && purchasePrice != null && purchasePrice.currency == asset.currentValue.currency) {
            val costResult = AssetFinancialCalculator.calculateCost(quantity, purchasePrice)
            if (costResult is CostCalculationResult.Success) {
                hasCost = true
                calculatedCostFormatted = MoneyFormatter.format(costResult.cost)
                val diffResult = AssetFinancialCalculator.calculateDifference(asset.currentValue, costResult.cost)
                if (diffResult is DifferenceCalculationResult.Success) {
                    val diff = diffResult.difference
                    val signPrefix = when {
                        diff.amountMinor > 0L -> "+ "
                        diff.amountMinor < 0L -> "- "
                        else -> ""
                    }
                    val absDiff = Money(kotlin.math.abs(diff.amountMinor), diff.currency)
                    differenceFormatted = "$signPrefix${MoneyFormatter.format(absDiff)}"

                    if (diff.amountMinor > 0L) {
                        isDiffPositive = true
                    } else if (diff.amountMinor < 0L) {
                        isDiffPositive = false
                    } else {
                        isDiffZero = true
                    }

                    diffResult.percentageBps?.let { bps ->
                        val pctAbs = kotlin.math.abs(bps) / 100
                        val pctSign = if (bps > 0) "+%" else if (bps < 0) "-%" else "%"
                        differencePercentageText = "($pctSign$pctAbs)"
                    }
                }
            }
        }

        val sourceLabel = when (valueSource) {
            AssetValueSource.MARKET_FRESH -> "Güncel piyasa"
            AssetValueSource.MARKET_STALE -> "Kayıtlı piyasa (eski)"
            AssetValueSource.MANUAL -> "Manuel giriş"
        }

        return AssetDetailDisplayModel(
            id = asset.id,
            name = asset.name,
            type = asset.type,
            typeLabel = asset.type.toDisplayLabel(),
            currentValueFormatted = MoneyFormatter.format(asset.currentValue),
            currency = asset.currentValue.currency,
            quantityFormatted = asset.quantity?.toDisplayText(),
            purchaseUnitPriceFormatted = asset.purchaseUnitPrice?.let(MoneyFormatter::format),
            calculatedCostFormatted = calculatedCostFormatted,
            differenceFormatted = differenceFormatted,
            differencePercentageText = differencePercentageText,
            isDifferencePositive = isDiffPositive,
            isDifferenceZero = isDiffZero,
            hasCalculatedCost = hasCost,
            valueSource = valueSource,
            valueSourceLabel = sourceLabel,
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
        val items = distribution.items.map { item ->
            val pct = item.percentageBps / 100
            AssetDistributionItemDisplayModel(
                type = item.type,
                typeLabel = item.type.toDisplayLabel(),
                formattedTotal = MoneyFormatter.format(item.total),
                percentageText = "%$pct",
                percentageBps = item.percentageBps,
                assetCount = item.assetCount,
            )
        }
        return AssetDistributionDisplayModel(
            currency = currency,
            overallTotalFormatted = MoneyFormatter.format(distribution.overallTotal),
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
    ): NetWorthDisplayModel? = when (result) {
        NetWorthCalculationResult.InvalidAssetValue,
        NetWorthCalculationResult.Overflow -> null
        is NetWorthCalculationResult.Success -> {
            val currencyTotals = result.totals.map { money ->
                val count = assets.count { it.currentValue.currency == money.currency }
                CurrencyTotalDisplayItem(
                    currency = money.currency,
                    formattedTotal = MoneyFormatter.format(money),
                    assetCount = count,
                )
            }
            val hasMultiple = result.totals.size > 1
            val primaryTotal = result.totals.firstOrNull()

            val hasMarketFresh = valueSources.values.any { it == AssetValueSource.MARKET_FRESH }
            val hasMarketStale = valueSources.values.any { it == AssetValueSource.MARKET_STALE }
            val hasManual = valueSources.values.any { it == AssetValueSource.MANUAL } || assets.any { !it.autoTrack }

            val sourceSummary = when {
                hasMarketFresh && !hasMarketStale && !hasManual -> "Güncel piyasa değerleri"
                hasMarketFresh && hasManual -> "Piyasa ve manuel değerler"
                hasMarketStale -> "Güncelliği doğrulanamayan değerler içerir"
                else -> "Manuel değerler"
            }

            NetWorthDisplayModel(
                totalsFormatted = result.totals.map(MoneyFormatter::format),
                currencyTotals = currencyTotals,
                assetCount = result.assetCount,
                hasMultipleCurrencies = hasMultiple,
                primaryCurrency = primaryTotal?.currency,
                primaryTotalFormatted = primaryTotal?.let(MoneyFormatter::format),
                sourceSummary = sourceSummary,
            )
        }
    }
}

fun AssetType.toDisplayLabel(): String = when (this) {
    AssetType.CASH -> "Nakit"
    AssetType.CRYPTO -> "Kripto"
    AssetType.STOCKS -> "Hisse senedi"
    AssetType.REAL_ESTATE -> "Gayrimenkul"
    AssetType.PRECIOUS_METALS -> "Değerli metal"
    AssetType.OTHER -> "Diğer"
}

fun AssetType.toSubtitle(): String = when (this) {
    AssetType.CASH -> "TL, döviz ve nakit varlıklar"
    AssetType.CRYPTO -> "Kripto para varlıkları"
    AssetType.STOCKS -> "Borsa hisseleri"
    AssetType.REAL_ESTATE -> "Konut, arsa ve diğer gayrimenkuller"
    AssetType.PRECIOUS_METALS -> "Altın, gümüş, platin vb."
    AssetType.OTHER -> "Sanat eseri, koleksiyon vb."
}

fun AssetQuantity.toDisplayText(): String {
    if (scale == 0) return unscaledValue.toString()
    val digits = unscaledValue.toString().padStart(scale + 1, '0')
    val fractional = digits.takeLast(scale).trimEnd('0')
    return if (fractional.isEmpty()) digits.dropLast(scale) else "${digits.dropLast(scale)},$fractional"
}
