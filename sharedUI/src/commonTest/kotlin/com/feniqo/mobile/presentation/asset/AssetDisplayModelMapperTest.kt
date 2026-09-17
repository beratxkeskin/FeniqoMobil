package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.usecase.NetWorthCalculationResult
import kotlinx.datetime.Instant
import kotlin.test.*

class AssetDisplayModelMapperTest {
    @Test
    fun map_formatsValuesAndUsesDeterministicTypeNameOrder() {
        val crypto = asset("a-2", "Bitcoin", AssetType.CRYPTO, 125_050L, AssetQuantity(12_340L, 4))
        val cashB = asset("a-3", "Vadeli", AssetType.CASH, 20_000L)
        val cashA = asset("a-1", "Acil Fon", AssetType.CASH, 10_000L)

        val result = AssetDisplayModelMapper.map(listOf(crypto, cashB, cashA))

        assertEquals(listOf("a-1", "a-3", "a-2"), result.map { it.id.value })
        assertEquals("1.250,50 ₺", result.last().currentValueFormatted)
        assertEquals("1,234", result.last().quantityFormatted)
        assertEquals("Kripto", result.last().typeLabel)
    }

    @Test
    fun netWorthMap_formatsCurrencyTotalsWithoutConversion() {
        val result = NetWorthDisplayModelMapper.map(
            NetWorthCalculationResult.Success(
                totals = listOf(Money(30_000L, Currency.TRY), Money(2_500L, Currency.USD)),
                assetCount = 3,
            ),
        )

        assertEquals(listOf("300,00 ₺", "25,00 $"), result?.totalsFormatted)
        assertEquals(3, result?.assetCount)
        assertTrue(result?.hasMultipleCurrencies == true)
        assertEquals(2, result?.currencyTotals?.size)
    }

    @Test
    fun mapDetail_calculatesCostAndDifference_whenFieldsPresent() {
        // 30 birim @ 4.000 TRY = 120.000 TRY maliyet. Güncel: 150.000 TRY -> Fark: +30.000 TRY (+%25)
        val item = Asset(
            id = EntityId("a-1"),
            ownerId = EntityId("owner"),
            workspaceId = null,
            name = "Gram altın",
            type = AssetType.PRECIOUS_METALS,
            currentValue = Money(15_000_000L, Currency.TRY),
            quantity = AssetQuantity(30L, 0),
            purchaseUnitPrice = Money(400_000L, Currency.TRY),
            trackingSymbol = null,
            autoTrack = false,
            createdAt = Instant.fromEpochMilliseconds(1L),
        )

        val detail = AssetDisplayModelMapper.mapDetail(item)
        assertTrue(detail.hasCalculatedCost)
        assertEquals("120.000,00 ₺", detail.calculatedCostFormatted)
        assertEquals("+ 30.000,00 ₺", detail.differenceFormatted)
        assertEquals("(+%25)", detail.differencePercentageText)
        assertEquals(true, detail.isDifferencePositive)
        assertFalse(detail.isPriceVerificationFailed)
    }

    @Test
    fun mapDetail_handlesMissingOptionalFieldsGracefully() {
        val item = Asset(
            id = EntityId("a-2"),
            ownerId = EntityId("owner"),
            workspaceId = null,
            name = "Nakit",
            type = AssetType.CASH,
            currentValue = Money(500_000L, Currency.TRY),
            quantity = null,
            purchaseUnitPrice = null,
            trackingSymbol = null,
            autoTrack = false,
            createdAt = Instant.fromEpochMilliseconds(1L),
        )

        val detail = AssetDisplayModelMapper.mapDetail(item)
        assertFalse(detail.hasCalculatedCost)
        assertNull(detail.calculatedCostFormatted)
        assertNull(detail.differenceFormatted)
        assertNull(detail.differencePercentageText)
    }

    @Test
    fun mapDistribution_producesCorrectBreakdownAndPercentages() {
        val assets = listOf(
            asset("1", "Gram Altın", AssetType.PRECIOUS_METALS, 15_000_000L),
            asset("2", "Hisse", AssetType.STOCKS, 7_500_000L),
            asset("3", "Nakit", AssetType.CASH, 2_500_000L),
        )

        val dist = AssetDisplayModelMapper.mapDistribution(assets, Currency.TRY)
        assertEquals("250.000,00 ₺", dist.overallTotalFormatted)
        assertEquals(3, dist.items.size)
        assertEquals("%60", dist.items[0].percentageText)
        assertEquals("%30", dist.items[1].percentageText)
        assertEquals("%10", dist.items[2].percentageText)
    }

    private fun asset(
        id: String,
        name: String,
        type: AssetType,
        value: Long,
        quantity: AssetQuantity? = null,
    ) = Asset(
        id = EntityId(id),
        ownerId = EntityId("owner-1"),
        workspaceId = null,
        name = name,
        type = type,
        currentValue = Money(value, Currency.TRY),
        quantity = quantity,
        purchaseUnitPrice = null,
        trackingSymbol = if (type == AssetType.CRYPTO) "BTC" else null,
        autoTrack = type == AssetType.CRYPTO,
        createdAt = Instant.fromEpochMilliseconds(1L),
    )
}
