package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AssetFinancialCalculatorTest {

    @Test
    fun calculateCost_scalesQuantityAndRoundsHalfUp() {
        // 30 birim @ 4.000,00 TRY = 120.000,00 TRY
        // miktar: 30 (unscaled = 30, scale = 0)
        // birim fiyat: 4.000,00 TRY (amountMinor = 400_000L)
        val q1 = AssetQuantity(30L, 0)
        val p1 = Money(400_000L, Currency.TRY)
        val res1 = assertIs<CostCalculationResult.Success>(AssetFinancialCalculator.calculateCost(q1, p1))
        assertEquals(12_000_000L, res1.cost.amountMinor) // 120.000,00 TRY

        // 0.5 adet @ 100,00 TRY = 50,00 TRY
        // miktar: 5 (scale = 1) -> 0.5
        // birim fiyat: 10_000L
        val q2 = AssetQuantity(5L, 1)
        val p2 = Money(10_000L, Currency.TRY)
        val res2 = assertIs<CostCalculationResult.Success>(AssetFinancialCalculator.calculateCost(q2, p2))
        assertEquals(5_000L, res2.cost.amountMinor) // 50,00 TRY

        // Yuvarlama kontrolü: 0.005 adet @ 100,00 TRY = 0.50 TRY
        val q3 = AssetQuantity(5L, 3)
        val p3 = Money(10_000L, Currency.TRY)
        val res3 = assertIs<CostCalculationResult.Success>(AssetFinancialCalculator.calculateCost(q3, p3))
        assertEquals(50L, res3.cost.amountMinor) // 0,50 TRY
    }

    @Test
    fun calculateCost_zeroHandlingAndOverflow() {
        val qZero = AssetQuantity(0L, 2)
        val price = Money(50_000L, Currency.TRY)
        val resZero = assertIs<CostCalculationResult.Success>(AssetFinancialCalculator.calculateCost(qZero, price))
        assertEquals(0L, resZero.cost.amountMinor)

        // Overflow: excessively large quantity and unit price
        val qHuge = AssetQuantity(Long.MAX_VALUE / 2, 0)
        val pHuge = Money(100_000L, Currency.TRY)
        val resOverflow = AssetFinancialCalculator.calculateCost(qHuge, pHuge)
        assertIs<CostCalculationResult.Overflow>(resOverflow)
    }

    @Test
    fun calculateDifference_mockupValues_matchesExpectedDiffAndPercentage() {
        // Mockup 02:
        // Güncel toplam değer: 150.000,00 TRY (15_000_000L)
        // Hesaplanan maliyet: 120.000,00 TRY (12_000_000L)
        // Değer farkı: +30.000,00 TRY (3_000_000L)
        // Oran: +%25 (2500 bps)
        val currentValue = Money(15_000_000L, Currency.TRY)
        val cost = Money(12_000_000L, Currency.TRY)
        val diffResult = assertIs<DifferenceCalculationResult.Success>(
            AssetFinancialCalculator.calculateDifference(currentValue, cost),
        )
        assertEquals(3_000_000L, diffResult.difference.amountMinor)
        assertEquals(2500, diffResult.percentageBps) // +25%
    }

    @Test
    fun calculateDifference_negativeAndZeroCost() {
        // Negatif fark: güncel 80.000 TRY, maliyet 100.000 TRY -> -20.000 TRY (-%20)
        val current = Money(8_000_000L, Currency.TRY)
        val cost = Money(10_000_000L, Currency.TRY)
        val diffNegative = assertIs<DifferenceCalculationResult.Success>(
            AssetFinancialCalculator.calculateDifference(current, cost),
        )
        assertEquals(-2_000_000L, diffNegative.difference.amountMinor)
        assertEquals(-2000, diffNegative.percentageBps) // -20%

        // Sıfır maliyet: yüzde hesaplanmamalı
        val diffZeroCost = assertIs<DifferenceCalculationResult.Success>(
            AssetFinancialCalculator.calculateDifference(current, Money.zero(Currency.TRY)),
        )
        assertEquals(8_000_000L, diffZeroCost.difference.amountMinor)
        assertNull(diffZeroCost.percentageBps)

        // Farklı para birimi
        val mismatch = AssetFinancialCalculator.calculateDifference(current, Money(100L, Currency.USD))
        assertIs<DifferenceCalculationResult.CurrencyMismatch>(mismatch)
    }

    @Test
    fun calculateDistribution_mockup01And03Breakdown() {
        // Mockup 01 / 03:
        // Toplam 250.000 TRY
        // Değerli metal: 150.000 TRY (%60)
        // Hisse senedi: 75.000 TRY (%30)
        // Nakit: 25.000 TRY (%10)
        val assets = listOf(
            createTestAsset("1", "Gram Altın", AssetType.PRECIOUS_METALS, 15_000_000L, Currency.TRY),
            createTestAsset("2", "Hisse Portföyü", AssetType.STOCKS, 7_500_000L, Currency.TRY),
            createTestAsset("3", "Nakit Birikim", AssetType.CASH, 2_500_000L, Currency.TRY),
            // USD varlığı filtre dışı kalmalı
            createTestAsset("4", "Dolar", AssetType.CASH, 200_000L, Currency.USD),
        )

        val distribution = AssetFinancialCalculator.calculateDistribution(assets, Currency.TRY)
        assertEquals(25_000_000L, distribution.overallTotal.amountMinor) // 250.000 TRY
        assertEquals(3, distribution.assetCount)
        assertEquals(3, distribution.items.size)

        // 1. Değerli metal (%60 -> 6000 bps)
        val item1 = distribution.items[0]
        assertEquals(AssetType.PRECIOUS_METALS, item1.type)
        assertEquals(15_000_000L, item1.total.amountMinor)
        assertEquals(6000, item1.percentageBps)

        // 2. Hisse senedi (%30 -> 3000 bps)
        val item2 = distribution.items[1]
        assertEquals(AssetType.STOCKS, item2.type)
        assertEquals(7_500_000L, item2.total.amountMinor)
        assertEquals(3000, item2.percentageBps)

        // 3. Nakit (%10 -> 1000 bps)
        val item3 = distribution.items[2]
        assertEquals(AssetType.CASH, item3.type)
        assertEquals(2_500_000L, item3.total.amountMinor)
        assertEquals(1000, item3.percentageBps)
    }

    private fun createTestAsset(
        id: String,
        name: String,
        type: AssetType,
        amountMinor: Long,
        currency: Currency,
    ) = Asset(
        id = EntityId(id),
        ownerId = EntityId("owner-1"),
        workspaceId = null,
        name = name,
        type = type,
        currentValue = Money(amountMinor, currency),
        quantity = null,
        purchaseUnitPrice = null,
        trackingSymbol = null,
        autoTrack = false,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )
}
