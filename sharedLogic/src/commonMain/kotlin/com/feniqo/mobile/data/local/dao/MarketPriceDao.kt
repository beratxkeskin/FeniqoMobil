package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.MarketPriceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketPriceDao {
    @Query(
        """
        SELECT * FROM market_prices
        WHERE asset_type_code = :assetTypeCode
          AND symbol = :symbol
          AND quote_currency_code = :quoteCurrencyCode
        LIMIT 1
        """,
    )
    fun observe(
        assetTypeCode: String,
        symbol: String,
        quoteCurrencyCode: String,
    ): Flow<MarketPriceEntity?>

    @Query(
        """
        SELECT * FROM market_prices
        WHERE asset_type_code = :assetTypeCode
          AND symbol = :symbol
          AND quote_currency_code = :quoteCurrencyCode
        LIMIT 1
        """,
    )
    suspend fun get(
        assetTypeCode: String,
        symbol: String,
        quoteCurrencyCode: String,
    ): MarketPriceEntity?

    @Upsert
    suspend fun upsertAll(prices: List<MarketPriceEntity>)
}
