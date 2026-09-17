package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta

sealed interface CostCalculationResult {
    data class Success(val cost: Money) : CostCalculationResult
    data object Overflow : CostCalculationResult
    data object Invalid : CostCalculationResult
}

sealed interface DifferenceCalculationResult {
    data class Success(
        val difference: MoneyDelta,
        val percentageBps: Int?, // Basis points: 2500 = +25%, null when cost is 0 or uncalculated
    ) : DifferenceCalculationResult

    data object CurrencyMismatch : DifferenceCalculationResult
    data object Overflow : DifferenceCalculationResult
}

data class TypeDistributionItem(
    val type: AssetType,
    val total: Money,
    val assetCount: Int,
    val percentageBps: Int, // 6000 = 60%, 1000 = 10%
)

data class AssetDistributionResult(
    val currency: Currency,
    val overallTotal: Money,
    val assetCount: Int,
    val items: List<TypeDistributionItem>,
)

/**
 * Varlık modülünde maliyet, değer farkı, getiri oranı ve portföy dağılımını
 * Double kullanmadan, ölçekli tam sayılar ve basis points ile hesaplar.
 */
object AssetFinancialCalculator {

    /**
     * Hesaplanan maliyet = miktar × alış birim fiyatı.
     * Miktarın ölçeği (scale) ve alış birim fiyatının minor birimi üzerinden HALF_UP ile yuvarlar.
     */
    fun calculateCost(quantity: AssetQuantity, unitPrice: Money): CostCalculationResult {
        val left = quantity.unscaledValue
        val right = unitPrice.amountMinor
        if (left < 0L || right < 0L) return CostCalculationResult.Invalid
        if (left == 0L || right == 0L) return CostCalculationResult.Success(Money.zero(unitPrice.currency))

        if (right > Long.MAX_VALUE / left) return CostCalculationResult.Overflow
        val product = left * right

        val discardedDigits = quantity.scale
        val amountMinor = if (discardedDigits == 0) {
            product
        } else {
            roundHalfUp(product, discardedDigits)
        }

        if (amountMinor > Money.MAX_AMOUNT_MINOR) return CostCalculationResult.Overflow
        return CostCalculationResult.Success(Money(amountMinor, unitPrice.currency))
    }

    /**
     * Değer farkı = güncel toplam değer − hesaplanan maliyet.
     * Yüzde fark = (değer farkı / hesaplanan maliyet) basis points.
     * Maliyet sıfır veya negatifse yüzde hesaplanmaz (null döner).
     */
    fun calculateDifference(currentValue: Money, cost: Money): DifferenceCalculationResult {
        if (currentValue.currency != cost.currency) {
            return DifferenceCalculationResult.CurrencyMismatch
        }

        val diff = currentValue.amountMinor - cost.amountMinor

        val percentageBps = if (cost.amountMinor <= 0L) {
            null
        } else {
            val absDiff = kotlin.math.abs(diff)
            if (absDiff > Long.MAX_VALUE / 10_000L) {
                return DifferenceCalculationResult.Overflow
            }
            val bpsLong = (diff * 10_000L) / cost.amountMinor
            if (bpsLong > Int.MAX_VALUE.toLong() || bpsLong < Int.MIN_VALUE.toLong()) {
                return DifferenceCalculationResult.Overflow
            }
            bpsLong.toInt()
        }

        return DifferenceCalculationResult.Success(
            difference = MoneyDelta(diff, currentValue.currency),
            percentageBps = percentageBps,
        )
    }

    /**
     * Seçilen para birimindeki varlıkların türlerine göre dağılımını hesaplar.
     * Farklı para birimlerindeki varlıklar filtrelenir; dönüşümsüz karıştırılmaz.
     */
    fun calculateDistribution(assets: List<Asset>, currency: Currency): AssetDistributionResult {
        val filtered = assets.filter { it.currentValue.currency == currency }
        if (filtered.isEmpty()) {
            return AssetDistributionResult(
                currency = currency,
                overallTotal = Money.zero(currency),
                assetCount = 0,
                items = emptyList(),
            )
        }

        var overallTotalMinor = 0L
        val typeGroups = mutableMapOf<AssetType, MutableList<Asset>>()
        for (asset in filtered) {
            val amount = asset.currentValue.amountMinor
            if (overallTotalMinor > Money.MAX_AMOUNT_MINOR - amount) {
                // Taşma koruması
                overallTotalMinor = Money.MAX_AMOUNT_MINOR
            } else {
                overallTotalMinor += amount
            }
            typeGroups.getOrPut(asset.type) { mutableListOf() }.add(asset)
        }

        val items = mutableListOf<TypeDistributionItem>()
        // Belirli ve tutarlı bir sıra: AssetType ordinal
        for (type in AssetType.entries) {
            val list = typeGroups[type] ?: continue
            var typeTotalMinor = 0L
            for (asset in list) {
                val amount = asset.currentValue.amountMinor
                if (typeTotalMinor > Money.MAX_AMOUNT_MINOR - amount) {
                    typeTotalMinor = Money.MAX_AMOUNT_MINOR
                } else {
                    typeTotalMinor += amount
                }
            }

            val percentageBps = if (overallTotalMinor <= 0L) {
                0
            } else {
                ((typeTotalMinor * 10_000L) / overallTotalMinor).toInt()
            }

            items.add(
                TypeDistributionItem(
                    type = type,
                    total = Money(typeTotalMinor, currency),
                    assetCount = list.size,
                    percentageBps = percentageBps,
                ),
            )
        }

        // Tutar büyüklüğüne göre azalan, eşitlikte ordinal
        items.sortWith(
            compareByDescending<TypeDistributionItem> { it.total.amountMinor }
                .thenBy { it.type.ordinal },
        )

        return AssetDistributionResult(
            currency = currency,
            overallTotal = Money(overallTotalMinor, currency),
            assetCount = filtered.size,
            items = items,
        )
    }

    private fun roundHalfUp(value: Long, discardedDigits: Int): Long {
        if (discardedDigits > 19) return 0L
        if (discardedDigits == 19) return if (value >= 5_000_000_000_000_000_000L) 1L else 0L
        val divisor = powerOfTen(discardedDigits) ?: return 0L
        val quotient = value / divisor
        val remainder = value % divisor
        return if (remainder >= divisor / 2L) quotient + 1L else quotient
    }

    private fun powerOfTen(exponent: Int): Long? {
        var result = 1L
        repeat(exponent) {
            if (result > Long.MAX_VALUE / 10L) return null
            result *= 10L
        }
        return result
    }
}
