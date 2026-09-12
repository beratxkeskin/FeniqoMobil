package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AssetFormModelsTest {
    @Test
    fun validInput_normalizesWithoutFloatingPoint() {
        val result = AssetFormInput(
            nameInput = "  Bitcoin  ",
            type = AssetType.CRYPTO,
            currentValueInput = "1250,50",
            currency = Currency.TRY,
            quantityInput = "0,00001234",
            purchaseUnitPriceInput = "1000",
            trackingSymbolInput = "  BTC  ",
            autoTrack = true,
        ).toDraft()

        val draft = assertIs<AssetFormNormalizationResult.Valid>(result).draft
        assertEquals("Bitcoin", draft.name)
        assertEquals(125_050L, draft.currentValue.amountMinor)
        assertEquals(1_234L, draft.quantity?.unscaledValue)
        assertEquals(8, draft.quantity?.scale)
        assertEquals(100_000L, draft.purchaseUnitPrice?.amountMinor)
        assertEquals("BTC", draft.trackingSymbol)
    }

    @Test
    fun zeroValues_areAcceptedAndOptionalFieldsRemainNull() {
        val result = AssetFormInput(nameInput = "Nakit", currentValueInput = "0,00").toDraft()
        val draft = assertIs<AssetFormNormalizationResult.Valid>(result).draft
        assertEquals(0L, draft.currentValue.amountMinor)
        assertNull(draft.quantity)
        assertNull(draft.purchaseUnitPrice)
        assertNull(draft.trackingSymbol)
    }

    @Test
    fun invalidFields_areReportedTogether() {
        val result = AssetFormInput(
            nameInput = " ",
            currentValueInput = "1,234",
            quantityInput = "1,1234567890123",
            purchaseUnitPriceInput = "abc",
            autoTrack = true,
        ).toDraft()

        val errors = assertIs<AssetFormNormalizationResult.Invalid>(result).errors
        assertEquals(AssetFormFieldError.NAME_REQUIRED, errors.name)
        assertEquals(AssetFormFieldError.CURRENT_VALUE_INVALID, errors.currentValue)
        assertEquals(AssetFormFieldError.QUANTITY_SCALE_EXCEEDED, errors.quantity)
        assertEquals(AssetFormFieldError.PURCHASE_PRICE_INVALID, errors.purchaseUnitPrice)
        assertEquals(AssetFormFieldError.TRACKING_SYMBOL_REQUIRED, errors.trackingSymbol)
    }

    @Test
    fun editDraft_buildsUpdateCommandWithSameIdentity() {
        val id = EntityId("asset-1")
        val draft = assertIs<AssetFormNormalizationResult.Valid>(
            AssetFormInput(assetId = id, nameInput = "Altın", currentValueInput = "500").toDraft(),
        ).draft
        assertEquals(id, draft.toUpdateCommand().id)
        assertEquals("Altın", draft.toCreateCommand().name)
    }
}
