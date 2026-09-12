package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.CreateAssetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateAssetCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.datetime.Instant

class AssetValidationRulesTest {
    @Test
    fun create_normalizes_name_and_tracking_symbol() {
        val result = AssetValidationRules.validateCreate(command(name = "  Bitcoin  ", symbol = " btc ", autoTrack = true))
        val valid = assertIs<AssetValidationResult.Valid<CreateAssetCommand>>(result)
        assertEquals("Bitcoin", valid.value.name)
        assertEquals("btc", valid.value.trackingSymbol)
    }

    @Test
    fun create_rejects_blank_name_and_missing_auto_tracking_symbol() {
        assertEquals(
            AssetValidationError.NAME_BLANK,
            assertIs<AssetValidationResult.Invalid>(AssetValidationRules.validateCreate(command(name = "  "))).error,
        )
        assertEquals(
            AssetValidationError.TRACKING_SYMBOL_REQUIRED,
            assertIs<AssetValidationResult.Invalid>(AssetValidationRules.validateCreate(command(symbol = null, autoTrack = true))).error,
        )
    }

    @Test
    fun create_rejects_purchase_price_currency_mismatch() {
        val result = AssetValidationRules.validateCreate(command(purchase = Money(100, Currency.USD)))
        assertEquals(AssetValidationError.PURCHASE_PRICE_CURRENCY_MISMATCH, assertIs<AssetValidationResult.Invalid>(result).error)
    }

    @Test
    fun apply_update_preserves_identity_and_owner_fields() {
        val existing = Asset(
            id = EntityId("asset-1"), ownerId = EntityId("owner-1"), workspaceId = null,
            name = "Nakit", type = AssetType.CASH, currentValue = Money(1000, Currency.TRY),
            quantity = null, purchaseUnitPrice = null, trackingSymbol = null, autoTrack = false,
            createdAt = Instant.parse("2026-09-08T00:00:00Z"),
        )
        val result = AssetValidationRules.applyUpdate(existing, UpdateAssetCommand(
            id = existing.id, name = "  Güncel Nakit ", type = AssetType.CASH,
            currentValue = Money(2500, Currency.TRY), quantity = AssetQuantity(25, 0),
        ))
        val valid = assertIs<AssetValidationResult.Valid<Asset>>(result)
        assertEquals(existing.id, valid.value.id)
        assertEquals(existing.ownerId, valid.value.ownerId)
        assertEquals("Güncel Nakit", valid.value.name)
        assertEquals(2500, valid.value.currentValue.amountMinor)
    }

    @Test
    fun quantity_must_be_non_negative_and_use_a_supported_scale() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { AssetQuantity(-1, 0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { AssetQuantity(1, 13) }
    }

    private fun command(
        name: String = "Altın",
        symbol: String? = null,
        autoTrack: Boolean = false,
        purchase: Money? = null,
    ) = CreateAssetCommand(
        name = name,
        type = AssetType.PRECIOUS_METALS,
        currentValue = Money(1000, Currency.TRY),
        purchaseUnitPrice = purchase,
        trackingSymbol = symbol,
        autoTrack = autoTrack,
    )
}
