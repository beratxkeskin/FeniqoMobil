package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.MarketPriceQuote
import com.feniqo.mobile.domain.model.MarketPriceRequest
import kotlinx.coroutines.flow.Flow

interface MarketPriceRepository {
    fun observe(request: MarketPriceRequest): Flow<MarketPriceQuote?>

    suspend fun refresh(requests: List<MarketPriceRequest>): RepositoryResult<List<MarketPriceQuote>>
}
