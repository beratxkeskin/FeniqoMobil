package com.feniqo.mobile.data.remote.marketprice

import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.MarketPriceAvailability
import com.feniqo.mobile.domain.model.MarketPriceQuote
import com.feniqo.mobile.domain.model.MarketPriceRequest
import com.feniqo.mobile.domain.model.ScaledMarketPrice
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseMarketPriceRemoteDataSource(
    private val client: SupabaseClient,
) : MarketPriceRemoteDataSource {
    override suspend fun fetch(requests: List<MarketPriceRequest>): List<RemoteMarketPriceResult> {
        val response = client.functions.invoke(
            function = FUNCTION_NAME,
            body = MarketPriceFunctionRequest(requests.map { it.toDto() }),
        )
        val decoded = response.body<MarketPriceFunctionResponse>()
        return decoded.toRemoteResults()
    }

    private fun MarketPriceRequest.toDto() = MarketPriceRequestDto(
        assetType = assetType.name,
        symbol = symbol,
        quoteCurrency = quoteCurrency.code,
    )

    private companion object {
        const val FUNCTION_NAME = "market-prices"
    }
}

@Serializable
private data class MarketPriceFunctionRequest(val items: List<MarketPriceRequestDto>)

@Serializable
private data class MarketPriceRequestDto(
    @SerialName("asset_type") val assetType: String,
    val symbol: String,
    @SerialName("quote_currency") val quoteCurrency: String,
)

@Serializable
internal data class MarketPriceFunctionResponse(
    val prices: List<MarketPriceResponseDto>,
    @SerialName("generated_at") val generatedAt: String,
)

@Serializable
internal data class MarketPriceResponseDto(
    @SerialName("asset_type") val assetType: String,
    val symbol: String,
    @SerialName("quote_currency") val quoteCurrency: String,
    @SerialName("price_unscaled") val priceUnscaled: String? = null,
    @SerialName("price_scale") val priceScale: Int? = null,
    @SerialName("observed_at") val observedAt: String? = null,
    @SerialName("fetched_at") val fetchedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val source: String? = null,
    val availability: String,
)

internal fun MarketPriceFunctionResponse.toRemoteResults(): List<RemoteMarketPriceResult> = prices.map { dto ->
    val currency = Currency.valueOf(dto.quoteCurrency)
    val unscaled = dto.priceUnscaled
    val scale = dto.priceScale
    val price = if (unscaled != null && scale != null) {
        ScaledMarketPrice(unscaled.toLong(), scale, currency)
    } else null
    RemoteMarketPriceResult(
        quote = MarketPriceQuote(
            assetType = AssetType.valueOf(dto.assetType),
            symbol = dto.symbol,
            price = price,
            observedAt = dto.observedAt?.let(Instant::parse),
            fetchedAt = Instant.parse(dto.fetchedAt ?: generatedAt),
            source = dto.source,
            availability = MarketPriceAvailability.valueOf(dto.availability),
        ),
        expiresAt = dto.expiresAt?.let(Instant::parse),
    )
}
