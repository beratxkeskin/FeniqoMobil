@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import feniqomobil.sharedui.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AssetLocalizedTextTest {
    @Test
    fun assetFormFieldError_mapsAll11EnumValuesExhaustively() {
        AssetFormFieldError.entries.forEach { error ->
            val resource = error.toLocalizedResource()
            assertNotNull(resource, "Resource mapping missing for $error")
        }
        assertEquals(11, AssetFormFieldError.entries.size)
        assertEquals(Res.string.asset_error_name_required, AssetFormFieldError.NAME_REQUIRED.toLocalizedResource())
        assertEquals(Res.string.asset_error_name_too_long, AssetFormFieldError.NAME_TOO_LONG.toLocalizedResource())
        assertEquals(Res.string.asset_error_current_value_required, AssetFormFieldError.CURRENT_VALUE_REQUIRED.toLocalizedResource())
        assertEquals(Res.string.asset_error_current_value_invalid, AssetFormFieldError.CURRENT_VALUE_INVALID.toLocalizedResource())
        assertEquals(Res.string.asset_error_current_value_too_large, AssetFormFieldError.CURRENT_VALUE_TOO_LARGE.toLocalizedResource())
        assertEquals(Res.string.asset_error_quantity_invalid, AssetFormFieldError.QUANTITY_INVALID.toLocalizedResource())
        assertEquals(Res.string.asset_error_quantity_scale_exceeded, AssetFormFieldError.QUANTITY_SCALE_EXCEEDED.toLocalizedResource())
        assertEquals(Res.string.asset_error_purchase_price_invalid, AssetFormFieldError.PURCHASE_PRICE_INVALID.toLocalizedResource())
        assertEquals(Res.string.asset_error_purchase_price_too_large, AssetFormFieldError.PURCHASE_PRICE_TOO_LARGE.toLocalizedResource())
        assertEquals(Res.string.asset_error_tracking_symbol_required, AssetFormFieldError.TRACKING_SYMBOL_REQUIRED.toLocalizedResource())
        assertEquals(Res.string.asset_error_tracking_symbol_too_long, AssetFormFieldError.TRACKING_SYMBOL_TOO_LONG.toLocalizedResource())
    }

    @Test
    fun assetType_mapsAllTypesToNameAndSubtitle() {
        AssetType.entries.forEach { type ->
            assertNotNull(type.toLocalizedName(), "Name missing for $type")
            assertNotNull(type.toLocalizedSubtitle(), "Subtitle missing for $type")
        }
        assertEquals(Res.string.asset_type_cash, AssetType.CASH.toLocalizedName())
        assertEquals(Res.string.asset_type_precious_metals, AssetType.PRECIOUS_METALS.toLocalizedName())
    }

    @Test
    fun assetValueSource_mapsAllEntries() {
        AssetValueSource.entries.forEach { source ->
            assertNotNull(source.toLocalizedLabelResource(), "Label missing for $source")
        }
        assertEquals(Res.string.asset_value_source_market_fresh, AssetValueSource.MARKET_FRESH.toLocalizedLabelResource())
        assertEquals(Res.string.asset_value_source_market_stale, AssetValueSource.MARKET_STALE.toLocalizedLabelResource())
        assertEquals(Res.string.asset_value_source_manual, AssetValueSource.MANUAL.toLocalizedLabelResource())
    }

    @Test
    fun netWorthSourceSummary_mapsAllEntries() {
        NetWorthSourceSummary.entries.forEach { summary ->
            assertNotNull(summary.toLocalizedResource(), "Resource missing for $summary")
        }
        assertEquals(Res.string.asset_source_summary_fresh_only, NetWorthSourceSummary.MARKET_FRESH_ONLY.toLocalizedResource())
        assertEquals(Res.string.asset_source_summary_fresh_and_manual, NetWorthSourceSummary.MARKET_AND_MANUAL.toLocalizedResource())
        assertEquals(Res.string.asset_source_summary_stale, NetWorthSourceSummary.MARKET_STALE.toLocalizedResource())
        assertEquals(Res.string.asset_source_summary_manual_only, NetWorthSourceSummary.MANUAL_ONLY.toLocalizedResource())
    }

    @Test
    fun assetQuantity_isExactlyOne_handlesBoundariesCorrectlyWithoutFloat() {
        val one = AssetQuantity(unscaledValue = 1L, scale = 0)
        val oneScaled = AssetQuantity(unscaledValue = 100L, scale = 2)
        val zero = AssetQuantity(unscaledValue = 0L, scale = 0)
        val two = AssetQuantity(unscaledValue = 2L, scale = 0)
        val thirty = AssetQuantity(unscaledValue = 30L, scale = 0)
        val oneAndHalf = AssetQuantity(unscaledValue = 15L, scale = 1)
        val half = AssetQuantity(unscaledValue = 5L, scale = 1)

        assertTrue(one.isExactlyOne())
        assertTrue(oneScaled.isExactlyOne())
        assertFalse(zero.isExactlyOne())
        assertFalse(two.isExactlyOne())
        assertFalse(thirty.isExactlyOne())
        assertFalse(oneAndHalf.isExactlyOne())
        assertFalse(half.isExactlyOne())

        assertEquals(1, resolveAssetQuantityPluralCount(one))
        assertEquals(1, resolveAssetQuantityPluralCount(oneScaled))
        assertEquals(2, resolveAssetQuantityPluralCount(zero))
        assertEquals(2, resolveAssetQuantityPluralCount(two))
        assertEquals(2, resolveAssetQuantityPluralCount(thirty))
        assertEquals(2, resolveAssetQuantityPluralCount(oneAndHalf))
        assertEquals(2, resolveAssetQuantityPluralCount(half))
    }
}
