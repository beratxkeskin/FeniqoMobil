package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.MarketPriceDao
import com.feniqo.mobile.data.local.entity.MarketPriceEntity
import com.feniqo.mobile.data.remote.marketprice.MarketPriceRemoteDataSource
import com.feniqo.mobile.data.remote.marketprice.RemoteMarketPriceResult
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.MarketPriceAvailability
import com.feniqo.mobile.domain.model.MarketPriceQuote
import com.feniqo.mobile.domain.model.MarketPriceRequest
import com.feniqo.mobile.domain.model.MarketPriceRequestValidationResult
import com.feniqo.mobile.domain.model.ScaledMarketPrice
import com.feniqo.mobile.domain.repository.MarketPriceRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.validation.MarketPriceRequestRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class OfflineFirstMarketPriceRepository(
    private val dao: MarketPriceDao,
    private val remote: MarketPriceRemoteDataSource,
    private val nowEpochMillisProvider: () -> Long,
) : MarketPriceRepository {

    override fun observe(request: MarketPriceRequest): Flow<MarketPriceQuote?> {
        val normalized = MarketPriceRequestRules.validate(request) as? MarketPriceRequestValidationResult.Valid
            ?: return kotlinx.coroutines.flow.flowOf(null)
        return normalized.request.let { valid ->
            dao.observe(valid.assetType.name, valid.symbol, valid.quoteCurrency.code)
                .map { it?.toDomain(nowEpochMillisProvider()) }
        }
    }

    override suspend fun refresh(requests: List<MarketPriceRequest>): RepositoryResult<List<MarketPriceQuote>> {
        if (requests.isEmpty()) return RepositoryResult.Success(emptyList())
        if (requests.size > MAX_BATCH_SIZE) {
            return RepositoryResult.Failure(AppError.Validation("market_price_batch_too_large"))
        }
        val normalized = ArrayList<MarketPriceRequest>(requests.size)
        for (request in requests) {
            when (val result = MarketPriceRequestRules.validate(request)) {
                is MarketPriceRequestValidationResult.Valid -> normalized += result.request
                is MarketPriceRequestValidationResult.Invalid -> {
                    return RepositoryResult.Failure(AppError.Validation(result.error.name.lowercase()))
                }
            }
        }

        return try {
            val results = remote.fetch(normalized)
            dao.upsertAll(results.mapNotNull { it.toCacheEntityOrNull() })
            RepositoryResult.Success(results.map(RemoteMarketPriceResult::quote))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            RepositoryResult.Failure(AppError.Network("market_price_refresh_failed"))
        }
    }

    private fun MarketPriceEntity.toDomain(nowEpochMillis: Long) = MarketPriceQuote(
        assetType = AssetType.valueOf(assetTypeCode),
        symbol = symbol,
        price = ScaledMarketPrice(priceUnscaled, priceScale, Currency.valueOf(quoteCurrencyCode)),
        observedAt = Instant.fromEpochMilliseconds(observedAtEpochMillis),
        fetchedAt = Instant.fromEpochMilliseconds(fetchedAtEpochMillis),
        source = source,
        availability = if (expiresAtEpochMillis > nowEpochMillis) {
            MarketPriceAvailability.FRESH
        } else {
            MarketPriceAvailability.STALE
        },
    )

    private fun RemoteMarketPriceResult.toCacheEntityOrNull(): MarketPriceEntity? {
        val value = quote.price ?: return null
        val observed = quote.observedAt ?: return null
        val cacheExpiry = expiresAt ?: return null
        val cacheSource = quote.source ?: return null
        return MarketPriceEntity(
            assetTypeCode = quote.assetType.name,
            symbol = quote.symbol,
            quoteCurrencyCode = value.currency.code,
            priceUnscaled = value.unscaledValue,
            priceScale = value.scale,
            observedAtEpochMillis = observed.toEpochMilliseconds(),
            fetchedAtEpochMillis = quote.fetchedAt.toEpochMilliseconds(),
            expiresAtEpochMillis = cacheExpiry.toEpochMilliseconds(),
            source = cacheSource,
        )
    }

    private companion object {
        const val MAX_BATCH_SIZE = 20
    }
}
