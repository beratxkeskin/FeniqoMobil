package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.MarketPriceRequest
import com.feniqo.mobile.domain.model.MarketPriceRequestError
import com.feniqo.mobile.domain.model.MarketPriceRequestValidationResult
import com.feniqo.mobile.domain.model.MarketValueCalculationError
import com.feniqo.mobile.domain.model.MarketValueCalculationResult
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.ScaledMarketPrice

object MarketPriceRequestRules {
    const val MAX_SYMBOL_LENGTH = 32
    private val SYMBOL_PATTERN = Regex("^[A-Z0-9][A-Z0-9._-]{0,31}$")

    fun validate(request: MarketPriceRequest): MarketPriceRequestValidationResult {
        if (request.assetType !in setOf(AssetType.CRYPTO, AssetType.STOCKS, AssetType.PRECIOUS_METALS)) {
            return MarketPriceRequestValidationResult.Invalid(MarketPriceRequestError.UNSUPPORTED_ASSET_TYPE)
        }
        val symbol = request.symbol.trim().uppercase()
        if (symbol.isEmpty()) return MarketPriceRequestValidationResult.Invalid(MarketPriceRequestError.SYMBOL_BLANK)
        if (symbol.length > MAX_SYMBOL_LENGTH) {
            return MarketPriceRequestValidationResult.Invalid(MarketPriceRequestError.SYMBOL_TOO_LONG)
        }
        if (!SYMBOL_PATTERN.matches(symbol)) {
            return MarketPriceRequestValidationResult.Invalid(MarketPriceRequestError.SYMBOL_INVALID)
        }
        return MarketPriceRequestValidationResult.Valid(request.copy(symbol = symbol))
    }
}

/** Miktar × birim fiyatı, para biriminin minor-unit ölçeğine HALF_UP ile dönüştürür. */
object MarketValueCalculator {
    fun calculate(
        quantity: AssetQuantity,
        unitPrice: ScaledMarketPrice,
    ): MarketValueCalculationResult {
        val left = quantity.unscaledValue
        val right = unitPrice.unscaledValue
        if (left != 0L && right > Long.MAX_VALUE / left) return overflow()
        val product = left * right
        val sourceScale = quantity.scale + unitPrice.scale
        val targetScale = unitPrice.currency.minorUnitDigits
        val amountMinor = when {
            sourceScale == targetScale -> product
            sourceScale < targetScale -> {
                val multiplier = powerOfTen(targetScale - sourceScale) ?: return overflow()
                if (product != 0L && multiplier > Money.MAX_AMOUNT_MINOR / product) return overflow()
                product * multiplier
            }
            else -> roundHalfUp(product, sourceScale - targetScale)
        }
        if (amountMinor > Money.MAX_AMOUNT_MINOR) return overflow()
        return MarketValueCalculationResult.Success(Money(amountMinor, unitPrice.currency))
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

    private fun overflow() = MarketValueCalculationResult.Invalid(MarketValueCalculationError.OVERFLOW)
}
