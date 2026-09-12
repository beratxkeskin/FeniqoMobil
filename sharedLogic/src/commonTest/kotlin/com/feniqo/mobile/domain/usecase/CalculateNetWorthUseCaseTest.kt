package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CalculateNetWorthUseCaseTest {
    private val calculator = CalculateNetWorthUseCase()

    @Test
    fun calculatesTotalsPerCurrencyWithoutImplicitConversion() {
        val result = assertIs<NetWorthCalculationResult.Success>(
            calculator(
                listOf(
                    asset("try-1", 10_000L, Currency.TRY),
                    asset("usd-1", 2_500L, Currency.USD),
                    asset("try-2", 20_000L, Currency.TRY),
                ),
            ),
        )

        assertEquals(listOf(Money(30_000L, Currency.TRY), Money(2_500L, Currency.USD)), result.totals)
        assertEquals(3, result.assetCount)
    }

    @Test
    fun emptyAssetsReturnsEmptySuccessfulSummary() {
        val result = assertIs<NetWorthCalculationResult.Success>(calculator(emptyList()))
        assertEquals(emptyList(), result.totals)
        assertEquals(0, result.assetCount)
    }

    @Test
    fun overflowFailsClosed() {
        assertIs<NetWorthCalculationResult.Overflow>(
            calculator(listOf(asset("max", Money.MAX_AMOUNT_MINOR, Currency.TRY), asset("one", 1L, Currency.TRY))),
        )
    }

    private fun asset(id: String, amountMinor: Long, currency: Currency) = Asset(
        id = EntityId(id),
        ownerId = EntityId("owner-1"),
        workspaceId = null,
        name = id,
        type = AssetType.CASH,
        currentValue = Money(amountMinor, currency),
        quantity = null,
        purchaseUnitPrice = null,
        trackingSymbol = null,
        autoTrack = false,
        createdAt = Instant.fromEpochMilliseconds(1L),
    )
}
