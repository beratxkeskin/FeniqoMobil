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
import kotlin.test.assertIs
import kotlin.test.assertNull

class AssetFormModelsTest {
    @Test
    fun validInput_normalizesWithoutFloatingPoint() {
        val result =
            AssetFormInput(
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
        val result =
            AssetFormInput(
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
        val draft =
            assertIs<AssetFormNormalizationResult.Valid>(
                AssetFormInput(assetId = id, nameInput = "Altın", currentValueInput = "500").toDraft(),
            ).draft
        assertEquals(id, draft.toUpdateCommand().id)
        assertEquals("Altın", draft.toCreateCommand().name)
    }

    @Test
    fun computeCost_calculatesLiveMoneyCost_whenValid() {
        val input =
            AssetFormInput(
                nameInput = "Gram altın",
                currentValueInput = "150000",
                quantityInput = "30",
                purchaseUnitPriceInput = "4000",
                currency = Currency.TRY,
            )
        val cost = input.computeCost()
        assertEquals(Money(120_000_00L, Currency.TRY), cost)

        val invalidInput = input.copy(quantityInput = "abc")
        assertNull(invalidInput.computeCost())
    }

    @Test
    fun fromDomain_formatsInputWithExplicitDecimalSeparator() {
        val asset =
            Asset(
                id = EntityId("asset-1"),
                ownerId = EntityId("owner-1"),
                workspaceId = null,
                name = "Bist Hissesi",
                type = AssetType.STOCKS,
                currentValue = Money(1250_50L, Currency.TRY),
                quantity = AssetQuantity(10L, 0),
                purchaseUnitPrice = Money(100_00L, Currency.TRY),
                trackingSymbol = null,
                autoTrack = false,
                createdAt = Instant.fromEpochMilliseconds(1L),
            )

        val trInput = AssetFormInput.fromDomain(asset, decimalSeparator = ',')
        assertEquals("1250,5", trInput.currentValueInput)
        assertEquals("10", trInput.quantityInput)
        assertEquals("100", trInput.purchaseUnitPriceInput)

        val enInput = AssetFormInput.fromDomain(asset, decimalSeparator = '.')
        assertEquals("1250.5", enInput.currentValueInput)
        assertEquals("10", enInput.quantityInput)
        assertEquals("100", enInput.purchaseUnitPriceInput)
    }
}
