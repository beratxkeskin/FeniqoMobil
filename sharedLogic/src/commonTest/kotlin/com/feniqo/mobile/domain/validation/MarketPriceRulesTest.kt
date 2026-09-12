package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.MarketPriceRequest
import com.feniqo.mobile.domain.model.MarketPriceRequestError
import com.feniqo.mobile.domain.model.MarketPriceRequestValidationResult
import com.feniqo.mobile.domain.model.MarketValueCalculationResult
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.ScaledMarketPrice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarketPriceRulesTest {
    @Test
    fun request_normalizesSymbolAndKeepsProviderIndependentFields() {
        val result = assertIs<MarketPriceRequestValidationResult.Valid>(
            MarketPriceRequestRules.validate(MarketPriceRequest(AssetType.CRYPTO, " btc-usd ", Currency.TRY)),
        )
        assertEquals("BTC-USD", result.request.symbol)
        assertEquals(Currency.TRY, result.request.quoteCurrency)
    }

    @Test
    fun request_rejectsUnsupportedTypeAndUnsafeSymbol() {
        assertEquals(
            MarketPriceRequestError.UNSUPPORTED_ASSET_TYPE,
            assertIs<MarketPriceRequestValidationResult.Invalid>(
                MarketPriceRequestRules.validate(MarketPriceRequest(AssetType.CASH, "TRY", Currency.TRY)),
            ).error,
        )
        assertEquals(
            MarketPriceRequestError.SYMBOL_INVALID,
            assertIs<MarketPriceRequestValidationResult.Invalid>(
                MarketPriceRequestRules.validate(MarketPriceRequest(AssetType.STOCKS, "https://evil", Currency.USD)),
            ).error,
        )
    }

    @Test
    fun calculator_multipliesScaledValuesWithoutFloatingPoint() {
        val result = assertIs<MarketValueCalculationResult.Success>(
            MarketValueCalculator.calculate(
                quantity = AssetQuantity(unscaledValue = 125L, scale = 2),
                unitPrice = ScaledMarketPrice(unscaledValue = 250_050L, scale = 2, currency = Currency.TRY),
            ),
        )
        assertEquals(Money(312_563L, Currency.TRY), result.value)
    }

    @Test
    fun calculator_roundsHalfUpToMinorUnits() {
        val roundedUp = assertIs<MarketValueCalculationResult.Success>(
            MarketValueCalculator.calculate(
                AssetQuantity(1L, 0),
                ScaledMarketPrice(12_345L, 3, Currency.TRY),
            ),
        )
        assertEquals(1_235L, roundedUp.value.amountMinor)
    }

    @Test
    fun calculator_failsClosedOnMultiplicationOverflow() {
        assertIs<MarketValueCalculationResult.Invalid>(
            MarketValueCalculator.calculate(
                AssetQuantity(Long.MAX_VALUE, 0),
                ScaledMarketPrice(2L, 0, Currency.USD),
            ),
        )
    }
}
