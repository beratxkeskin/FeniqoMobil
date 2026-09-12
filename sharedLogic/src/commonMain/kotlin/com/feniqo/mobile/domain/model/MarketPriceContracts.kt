package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

/** Sağlayıcıdan bağımsız, kayan nokta kullanmayan birim piyasa fiyatı. */
data class ScaledMarketPrice(
    val unscaledValue: Long,
    val scale: Int,
    val currency: Currency,
) {
    init {
        require(unscaledValue > 0L) { "Piyasa fiyatı sıfırdan büyük olmalıdır." }
        require(scale in 0..12) { "Piyasa fiyatı ölçeği 0 ile 12 arasında olmalıdır." }
    }
}

data class MarketPriceRequest(
    val assetType: AssetType,
    val symbol: String,
    val quoteCurrency: Currency,
)

enum class MarketPriceAvailability {
    FRESH,
    STALE,
    UNAVAILABLE,
    UNSUPPORTED,
}

data class MarketPriceQuote(
    val assetType: AssetType,
    val symbol: String,
    val price: ScaledMarketPrice?,
    val observedAt: Instant?,
    val fetchedAt: Instant,
    val source: String?,
    val availability: MarketPriceAvailability,
) {
    init {
        val hasPrice = price != null
        require(hasPrice == (observedAt != null)) { "Fiyat ve gözlem zamanı birlikte bulunmalıdır." }
        require(hasPrice == !source.isNullOrBlank()) { "Fiyat ve kaynak bilgisi birlikte bulunmalıdır." }
        require(availability !in setOf(MarketPriceAvailability.FRESH, MarketPriceAvailability.STALE) || hasPrice) {
            "Kullanılabilir piyasa sonucu fiyat içermelidir."
        }
        require(availability !in setOf(MarketPriceAvailability.UNAVAILABLE, MarketPriceAvailability.UNSUPPORTED) || !hasPrice) {
            "Kullanılamayan piyasa sonucu fiyat içermemelidir."
        }
    }
}

enum class MarketPriceRequestError {
    UNSUPPORTED_ASSET_TYPE,
    SYMBOL_BLANK,
    SYMBOL_TOO_LONG,
    SYMBOL_INVALID,
}

sealed interface MarketPriceRequestValidationResult {
    data class Valid(val request: MarketPriceRequest) : MarketPriceRequestValidationResult
    data class Invalid(val error: MarketPriceRequestError) : MarketPriceRequestValidationResult
}

enum class MarketValueCalculationError {
    OVERFLOW,
}

sealed interface MarketValueCalculationResult {
    data class Success(val value: Money) : MarketValueCalculationResult
    data class Invalid(val error: MarketValueCalculationError) : MarketValueCalculationResult
}
