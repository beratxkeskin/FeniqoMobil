package com.feniqo.mobile.data.remote.marketprice

import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.MarketPriceAvailability
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarketPriceFunctionContractTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun freshFunctionResponse_preservesExactScaledPriceAndExpiry() {
        val response = json.decodeFromString<MarketPriceFunctionResponse>(
            """{"prices":[{"asset_type":"STOCKS","symbol":"AAPL","quote_currency":"USD","price_unscaled":"1875012","price_scale":4,"observed_at":"2026-09-09T10:00:00Z","fetched_at":"2026-09-09T10:00:01Z","expires_at":"2026-09-09T10:05:01Z","source":"test-provider","availability":"FRESH"}],"generated_at":"2026-09-09T10:00:01Z"}""",
        )

        val result = response.toRemoteResults().single()
        assertEquals(AssetType.STOCKS, result.quote.assetType)
        assertEquals(1_875_012L, result.quote.price?.unscaledValue)
        assertEquals(4, result.quote.price?.scale)
        assertEquals(Currency.USD, result.quote.price?.currency)
        assertEquals(MarketPriceAvailability.FRESH, result.quote.availability)
        assertEquals("2026-09-09T10:05:01Z", result.expiresAt.toString())
    }

    @Test
    fun unavailableFunctionResponse_usesGeneratedAtAndContainsNoPrice() {
        val response = json.decodeFromString<MarketPriceFunctionResponse>(
            """{"prices":[{"asset_type":"CRYPTO","symbol":"BTC","quote_currency":"TRY","availability":"UNAVAILABLE"}],"generated_at":"2026-09-09T11:00:00Z"}""",
        )

        val result = response.toRemoteResults().single()
        assertEquals(MarketPriceAvailability.UNAVAILABLE, result.quote.availability)
        assertEquals("2026-09-09T11:00:00Z", result.quote.fetchedAt.toString())
        assertNull(result.quote.price)
        assertNull(result.quote.observedAt)
        assertNull(result.quote.source)
        assertNull(result.expiresAt)
    }
}
