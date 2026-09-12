package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.MarketPriceDao
import com.feniqo.mobile.data.local.entity.MarketPriceEntity
import com.feniqo.mobile.data.remote.marketprice.MarketPriceRemoteDataSource
import com.feniqo.mobile.data.remote.marketprice.RemoteMarketPriceResult
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.MarketPriceAvailability
import com.feniqo.mobile.domain.model.MarketPriceQuote
import com.feniqo.mobile.domain.model.MarketPriceRequest
import com.feniqo.mobile.domain.model.ScaledMarketPrice
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class OfflineFirstMarketPriceRepositoryTest {

    @Test
    fun refresh_normalizes_request_and_caches_priced_result() = runTest {
        val dao = FakeMarketPriceDao()
        val remote = FakeRemoteDataSource { requests ->
            assertEquals("AAPL", requests.single().symbol)
            listOf(remoteResult())
        }
        val repository = repository(dao, remote)

        val result = repository.refresh(listOf(request(symbol = " aapl ")))

        assertIs<RepositoryResult.Success<List<MarketPriceQuote>>>(result)
        assertEquals(18_750L, dao.get("STOCKS", "AAPL", "USD")?.priceUnscaled)
        assertEquals(MarketPriceAvailability.FRESH, repository.observe(request()).first()?.availability)
    }

    @Test
    fun observe_marks_expired_cache_as_stale() = runTest {
        val dao = FakeMarketPriceDao()
        dao.upsertAll(listOf(cacheEntity(expiresAt = 1_999L)))

        assertEquals(
            MarketPriceAvailability.STALE,
            repository(dao, FakeRemoteDataSource { emptyList() }).observe(request()).first()?.availability,
        )
    }

    @Test
    fun invalid_request_does_not_call_remote() = runTest {
        val remote = FakeRemoteDataSource { error("remote çağrılmamalı") }
        val result = repository(FakeMarketPriceDao(), remote).refresh(listOf(request(symbol = "bad symbol")))

        assertIs<RepositoryResult.Failure>(result)
        assertEquals(0, remote.callCount)
    }

    @Test
    fun unavailable_result_does_not_replace_existing_cache() = runTest {
        val dao = FakeMarketPriceDao()
        dao.upsertAll(listOf(cacheEntity(unscaled = 100L)))
        val unavailable = RemoteMarketPriceResult(
            quote = MarketPriceQuote(
                assetType = AssetType.STOCKS,
                symbol = "AAPL",
                price = null,
                observedAt = null,
                fetchedAt = Instant.fromEpochMilliseconds(2_000L),
                source = null,
                availability = MarketPriceAvailability.UNAVAILABLE,
            ),
            expiresAt = null,
        )

        val result = repository(dao, FakeRemoteDataSource { listOf(unavailable) }).refresh(listOf(request()))

        assertIs<RepositoryResult.Success<List<MarketPriceQuote>>>(result)
        assertEquals(100L, dao.get("STOCKS", "AAPL", "USD")?.priceUnscaled)
    }

    @Test
    fun cancellation_is_not_converted_to_network_failure() = runTest {
        val repository = repository(
            FakeMarketPriceDao(),
            FakeRemoteDataSource { throw CancellationException("cancel") },
        )

        assertFailsWith<CancellationException> { repository.refresh(listOf(request())) }
    }

    @Test
    fun invalid_observation_emits_null() = runTest {
        assertNull(
            repository(FakeMarketPriceDao(), FakeRemoteDataSource { emptyList() })
                .observe(request(symbol = "?"))
                .first(),
        )
    }

    private fun repository(dao: MarketPriceDao, remote: MarketPriceRemoteDataSource) =
        OfflineFirstMarketPriceRepository(dao, remote) { 2_000L }

    private fun request(symbol: String = "AAPL") = MarketPriceRequest(
        assetType = AssetType.STOCKS,
        symbol = symbol,
        quoteCurrency = Currency.USD,
    )

    private fun remoteResult() = RemoteMarketPriceResult(
        quote = MarketPriceQuote(
            assetType = AssetType.STOCKS,
            symbol = "AAPL",
            price = ScaledMarketPrice(18_750L, 2, Currency.USD),
            observedAt = Instant.fromEpochMilliseconds(1_000L),
            fetchedAt = Instant.fromEpochMilliseconds(1_100L),
            source = "test-provider",
            availability = MarketPriceAvailability.FRESH,
        ),
        expiresAt = Instant.fromEpochMilliseconds(3_000L),
    )

    private fun cacheEntity(unscaled: Long = 18_750L, expiresAt: Long = 3_000L) = MarketPriceEntity(
        assetTypeCode = "STOCKS",
        symbol = "AAPL",
        quoteCurrencyCode = "USD",
        priceUnscaled = unscaled,
        priceScale = 2,
        observedAtEpochMillis = 1_000L,
        fetchedAtEpochMillis = 1_100L,
        expiresAtEpochMillis = expiresAt,
        source = "test-provider",
    )
}

private class FakeRemoteDataSource(
    private val result: suspend (List<MarketPriceRequest>) -> List<RemoteMarketPriceResult>,
) : MarketPriceRemoteDataSource {
    var callCount = 0

    override suspend fun fetch(requests: List<MarketPriceRequest>): List<RemoteMarketPriceResult> {
        callCount += 1
        return result(requests)
    }
}

private class FakeMarketPriceDao : MarketPriceDao {
    private val records = LinkedHashMap<String, MarketPriceEntity>()
    private val changes = MutableStateFlow(0)

    override fun observe(assetTypeCode: String, symbol: String, quoteCurrencyCode: String): Flow<MarketPriceEntity?> =
        changes.map { records[key(assetTypeCode, symbol, quoteCurrencyCode)] }

    override suspend fun get(assetTypeCode: String, symbol: String, quoteCurrencyCode: String): MarketPriceEntity? =
        records[key(assetTypeCode, symbol, quoteCurrencyCode)]

    override suspend fun upsertAll(prices: List<MarketPriceEntity>) {
        prices.forEach { records[key(it.assetTypeCode, it.symbol, it.quoteCurrencyCode)] = it }
        changes.value += 1
    }

    private fun key(type: String, symbol: String, currency: String) = "$type|$symbol|$currency"
}
