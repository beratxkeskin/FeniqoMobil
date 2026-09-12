package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_13_14
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.MarketPriceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class MarketPriceDaoTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeniqoDatabase::class.java,
    )

    @Test
    fun upsertAll_keeps_quotes_separate_by_type_symbol_and_currency() = runTest {
        val database = inMemoryDatabase()
        try {
            database.marketPriceDao().upsertAll(
                listOf(
                    price(symbol = "AAPL", currency = "USD", unscaled = 18_750L),
                    price(symbol = "AAPL", currency = "TRY", unscaled = 760_000L),
                ),
            )

            assertEquals(
                18_750L,
                database.marketPriceDao().observe("STOCK", "AAPL", "USD").first()?.priceUnscaled,
            )
            assertEquals(
                760_000L,
                database.marketPriceDao().get("STOCK", "AAPL", "TRY")?.priceUnscaled,
            )
            assertNull(database.marketPriceDao().get("CRYPTO", "AAPL", "USD"))
        } finally {
            database.close()
        }
    }

    @Test
    fun upsertAll_replaces_same_quote_without_deleting_stale_rows() = runTest {
        val database = inMemoryDatabase()
        try {
            database.marketPriceDao().upsertAll(listOf(price(unscaled = 100L, expiresAt = 2_000L)))
            database.marketPriceDao().upsertAll(listOf(price(unscaled = 125L, expiresAt = 3_000L)))

            val stored = database.marketPriceDao().get("STOCK", "AAPL", "USD")
            assertEquals(125L, stored?.priceUnscaled)
            assertEquals(3_000L, stored?.expiresAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun migration_13_to_14_creates_market_price_cache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "market_price_migration_13_14.db"
        val databasePath = context.getDatabasePath(databaseName).absolutePath
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databasePath, 13).close()
        val migrated = migrationHelper.runMigrationsAndValidate(
            databasePath,
            14,
            true,
            ANDROID_MIGRATION_13_14,
        )
        try {
            migrated.execSQL(
                """
                INSERT INTO market_prices (
                    asset_type_code, symbol, quote_currency_code, price_unscaled, price_scale,
                    observed_at_epoch_ms, fetched_at_epoch_ms, expires_at_epoch_ms, source
                ) VALUES ('CRYPTO', 'BTC', 'USD', 6500012, 2, 1000, 1100, 2100, 'test')
                """.trimIndent(),
            )
            val cursor = migrated.query(
                "SELECT price_unscaled, price_scale, source FROM market_prices WHERE symbol = 'BTC'",
            )
            cursor.use {
                assertEquals(true, it.moveToFirst())
                assertEquals(6_500_012L, it.getLong(0))
                assertEquals(2, it.getInt(1))
                assertEquals("test", it.getString(2))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun inMemoryDatabase(): FeniqoDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }

    private fun price(
        symbol: String = "AAPL",
        currency: String = "USD",
        unscaled: Long = 18_750L,
        expiresAt: Long = 2_000L,
    ) = MarketPriceEntity(
        assetTypeCode = "STOCK",
        symbol = symbol,
        quoteCurrencyCode = currency,
        priceUnscaled = unscaled,
        priceScale = 2,
        observedAtEpochMillis = 1_000L,
        fetchedAtEpochMillis = 1_100L,
        expiresAtEpochMillis = expiresAt,
        source = "test",
    )
}
