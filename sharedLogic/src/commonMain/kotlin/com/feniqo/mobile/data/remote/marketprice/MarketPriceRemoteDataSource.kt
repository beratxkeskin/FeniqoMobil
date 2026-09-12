package com.feniqo.mobile.data.remote.marketprice

import com.feniqo.mobile.domain.model.MarketPriceQuote
import com.feniqo.mobile.domain.model.MarketPriceRequest
import kotlinx.datetime.Instant

data class RemoteMarketPriceResult(
    val quote: MarketPriceQuote,
    val expiresAt: Instant?,
)

interface MarketPriceRemoteDataSource {
    suspend fun fetch(requests: List<MarketPriceRequest>): List<RemoteMarketPriceResult>
}
