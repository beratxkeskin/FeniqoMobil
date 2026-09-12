package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.feniqo.mobile.domain.usecase.NetWorthCalculationResult

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
