package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Sağlayıcıdan bağımsız, cihazda stale fallback için de saklanan piyasa fiyatı önbelleği. */
@Entity(
    tableName = "market_prices",
    primaryKeys = ["asset_type_code", "symbol", "quote_currency_code"],
    indices = [
        Index(value = ["expires_at_epoch_ms"]),
        Index(value = ["fetched_at_epoch_ms"]),
    ],
)
data class MarketPriceEntity(
    @ColumnInfo(name = "asset_type_code")
    val assetTypeCode: String,
    @ColumnInfo(name = "symbol")
    val symbol: String,
    @ColumnInfo(name = "quote_currency_code")
    val quoteCurrencyCode: String,
    @ColumnInfo(name = "price_unscaled")
    val priceUnscaled: Long,
    @ColumnInfo(name = "price_scale")
    val priceScale: Int,
    @ColumnInfo(name = "observed_at_epoch_ms")
    val observedAtEpochMillis: Long,
    @ColumnInfo(name = "fetched_at_epoch_ms")
    val fetchedAtEpochMillis: Long,
    @ColumnInfo(name = "expires_at_epoch_ms")
    val expiresAtEpochMillis: Long,
    @ColumnInfo(name = "source")
    val source: String,
)
