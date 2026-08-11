package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

enum class AssetType {
    CASH,
    CRYPTO,
    STOCKS,
    REAL_ESTATE,
    PRECIOUS_METALS,
    OTHER,
}

/** Adet bilgisini Double kullanmadan, ölçekli tam sayı olarak saklar. */
data class AssetQuantity(
    val unscaledValue: Long,
    val scale: Int,
) {
    init {
        require(unscaledValue >= 0) { "Varlık miktarı negatif olamaz." }
        require(scale in 0..12) { "Varlık miktarı ölçeği 0 ile 12 arasında olmalıdır." }
    }
}

data class Asset(
    val id: EntityId,
    val ownerId: EntityId,
    val workspaceId: EntityId?,
    val name: String,
    val type: AssetType,
    val currentValue: Money,
    val quantity: AssetQuantity?,
    val purchaseUnitPrice: Money?,
    val trackingSymbol: String?,
    val autoTrack: Boolean,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Varlık adı boş olamaz." }
        require(purchaseUnitPrice == null || purchaseUnitPrice.currency == currentValue.currency) {
            "Alış ve güncel değer aynı para biriminde olmalıdır."
        }
        require(!autoTrack || !trackingSymbol.isNullOrBlank()) {
            "Otomatik takip edilen varlığın piyasa sembolü olmalıdır."
        }
    }
}

/** Güvenilir backend'den gelen, kaynak zamanı belli piyasa fiyatı. */
data class MarketPrice(
    val symbol: String,
    val price: Money,
    val observedAt: Instant,
    val source: String,
) {
    init {
        require(symbol.isNotBlank()) { "Piyasa sembolü boş olamaz." }
        require(price.amountMinor > 0) { "Piyasa fiyatı sıfırdan büyük olmalıdır." }
        require(source.isNotBlank()) { "Piyasa fiyatı kaynağı boş olamaz." }
    }
}
