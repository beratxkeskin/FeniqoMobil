package com.feniqo.mobile.presentation.asset

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.CreateAssetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateAssetCommand
import com.feniqo.mobile.domain.validation.AssetValidationRules
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.util.MoneyFormatter
import com.feniqo.mobile.presentation.common.FinanceUiMessage

enum class AssetFormFieldError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    CURRENT_VALUE_REQUIRED,
    CURRENT_VALUE_INVALID,
    CURRENT_VALUE_TOO_LARGE,
    QUANTITY_INVALID,
    QUANTITY_SCALE_EXCEEDED,
    PURCHASE_PRICE_INVALID,
    PURCHASE_PRICE_TOO_LARGE,
    TRACKING_SYMBOL_REQUIRED,
    TRACKING_SYMBOL_TOO_LONG,
}

fun AssetFormFieldError.toDisplayText(): String = when (this) {
    AssetFormFieldError.NAME_REQUIRED -> "Varlık adı zorunludur."
    AssetFormFieldError.NAME_TOO_LONG -> "Varlık adı en fazla 120 karakter olabilir."
    AssetFormFieldError.CURRENT_VALUE_REQUIRED -> "Güncel değer zorunludur."
    AssetFormFieldError.CURRENT_VALUE_INVALID -> "Geçerli bir güncel değer girin."
    AssetFormFieldError.CURRENT_VALUE_TOO_LARGE -> "Güncel değer desteklenen sınırı aşıyor."
    AssetFormFieldError.QUANTITY_INVALID -> "Geçerli bir miktar girin."
    AssetFormFieldError.QUANTITY_SCALE_EXCEEDED -> "Miktar en fazla 12 ondalık basamak içerebilir."
    AssetFormFieldError.PURCHASE_PRICE_INVALID -> "Geçerli bir alış birim fiyatı girin."
    AssetFormFieldError.PURCHASE_PRICE_TOO_LARGE -> "Alış fiyatı desteklenen sınırı aşıyor."
    AssetFormFieldError.TRACKING_SYMBOL_REQUIRED -> "Otomatik takip için piyasa sembolü zorunludur."
    AssetFormFieldError.TRACKING_SYMBOL_TOO_LONG -> "Piyasa sembolü en fazla 32 karakter olabilir."
}

data class AssetFormErrors(
    val name: AssetFormFieldError? = null,
    val currentValue: AssetFormFieldError? = null,
    val quantity: AssetFormFieldError? = null,
    val purchaseUnitPrice: AssetFormFieldError? = null,
    val trackingSymbol: AssetFormFieldError? = null,
) {
    val hasErrors: Boolean
        get() = name != null || currentValue != null || quantity != null ||
            purchaseUnitPrice != null || trackingSymbol != null
}

data class AssetFormDraft(
    val assetId: EntityId?,
    val name: String,
    val type: AssetType,
    val currentValue: Money,
    val quantity: AssetQuantity?,
    val purchaseUnitPrice: Money?,
    val trackingSymbol: String?,
    val autoTrack: Boolean,
) {
    fun toCreateCommand(): CreateAssetCommand = CreateAssetCommand(
        name = name,
        type = type,
        currentValue = currentValue,
        quantity = quantity,
        purchaseUnitPrice = purchaseUnitPrice,
        trackingSymbol = trackingSymbol,
        autoTrack = autoTrack,
    )

    fun toUpdateCommand(): UpdateAssetCommand = UpdateAssetCommand(
        id = requireNotNull(assetId) { "Düzenleme için varlık kimliği zorunludur." },
        name = name,
        type = type,
        currentValue = currentValue,
        quantity = quantity,
        purchaseUnitPrice = purchaseUnitPrice,
        trackingSymbol = trackingSymbol,
        autoTrack = autoTrack,
    )
}

sealed interface AssetFormNormalizationResult {
    data class Valid(val draft: AssetFormDraft) : AssetFormNormalizationResult
    data class Invalid(val errors: AssetFormErrors) : AssetFormNormalizationResult
}

data class AssetFormUiState(
    val input: AssetFormInput = AssetFormInput(),
    val errors: AssetFormErrors = AssetFormErrors(),
    val isSubmitting: Boolean = false,
    val pendingDeleteConfirmation: Boolean = false,
)

sealed interface AssetEditLoadState {
    data object Idle : AssetEditLoadState
    data object Loading : AssetEditLoadState
    data object Ready : AssetEditLoadState
    data object NotFound : AssetEditLoadState
    data class Error(val message: FinanceUiMessage) : AssetEditLoadState
}

sealed interface AssetFormUiEvent {
    data class MutationSuccess(val message: FinanceUiMessage) : AssetFormUiEvent
    data class ShowMessage(val message: FinanceUiMessage) : AssetFormUiEvent
}

data class AssetFormInput(
    val assetId: EntityId? = null,
    val nameInput: String = "",
    val type: AssetType = AssetType.CASH,
    val currentValueInput: String = "",
    val currency: Currency = Currency.TRY,
    val quantityInput: String = "",
    val purchaseUnitPriceInput: String = "",
    val trackingSymbolInput: String = "",
    val autoTrack: Boolean = false,
) {
    fun toDraft(): AssetFormNormalizationResult {
        val name = nameInput.trim()
        val nameError = when {
            name.isEmpty() -> AssetFormFieldError.NAME_REQUIRED
            name.length > AssetValidationRules.MAX_NAME_LENGTH -> AssetFormFieldError.NAME_TOO_LONG
            else -> null
        }

        val currentValueResult = parseMoneyAllowingZero(currentValueInput, currency, required = true)
        val quantityResult = parseQuantity(quantityInput)
        val purchaseResult = parseMoneyAllowingZero(purchaseUnitPriceInput, currency, required = false)
        val symbol = trackingSymbolInput.trim().takeIf(String::isNotEmpty)
        val symbolError = when {
            symbol != null && symbol.length > AssetValidationRules.MAX_TRACKING_SYMBOL_LENGTH ->
                AssetFormFieldError.TRACKING_SYMBOL_TOO_LONG
            autoTrack && symbol == null -> AssetFormFieldError.TRACKING_SYMBOL_REQUIRED
            else -> null
        }

        val errors = AssetFormErrors(
            name = nameError,
            currentValue = currentValueResult.error,
            quantity = quantityResult.error,
            purchaseUnitPrice = purchaseResult.error,
            trackingSymbol = symbolError,
        )
        if (errors.hasErrors || currentValueResult.money == null) {
            return AssetFormNormalizationResult.Invalid(errors)
        }
        return AssetFormNormalizationResult.Valid(
            AssetFormDraft(
                assetId = assetId,
                name = name,
                type = type,
                currentValue = currentValueResult.money,
                quantity = quantityResult.quantity,
                purchaseUnitPrice = purchaseResult.money,
                trackingSymbol = symbol,
                autoTrack = autoTrack,
            ),
        )
    }

    companion object {
        fun fromDomain(asset: Asset): AssetFormInput = AssetFormInput(
            assetId = asset.id,
            nameInput = asset.name,
            type = asset.type,
            currentValueInput = MoneyFormatter.formatMinorUnitsToInputText(
                asset.currentValue.amountMinor,
                asset.currentValue.currency,
            ),
            currency = asset.currentValue.currency,
            quantityInput = asset.quantity?.toInputText().orEmpty(),
            purchaseUnitPriceInput = asset.purchaseUnitPrice?.let {
                MoneyFormatter.formatMinorUnitsToInputText(it.amountMinor, it.currency)
            }.orEmpty(),
            trackingSymbolInput = asset.trackingSymbol.orEmpty(),
            autoTrack = asset.autoTrack,
        )
    }
}

private data class MoneyFieldResult(val money: Money?, val error: AssetFormFieldError?)

private fun parseMoneyAllowingZero(input: String, currency: Currency, required: Boolean): MoneyFieldResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return MoneyFieldResult(null, if (required) AssetFormFieldError.CURRENT_VALUE_REQUIRED else null)
    }
    if (trimmed.isZeroDecimal()) return MoneyFieldResult(Money.zero(currency), null)
    return when (val parsed = MoneyAmountParser.parseToMinorUnits(trimmed, currency)) {
        is MoneyAmountParser.ParseResult.Success -> MoneyFieldResult(Money(parsed.amountMinor, currency), null)
        is MoneyAmountParser.ParseResult.Invalid -> MoneyFieldResult(
            null,
            when (parsed.error) {
                MoneyAmountParser.MoneyParseError.EMPTY -> if (required) {
                    AssetFormFieldError.CURRENT_VALUE_REQUIRED
                } else {
                    AssetFormFieldError.PURCHASE_PRICE_INVALID
                }
                MoneyAmountParser.MoneyParseError.MAX_AMOUNT_EXCEEDED -> if (required) {
                    AssetFormFieldError.CURRENT_VALUE_TOO_LARGE
                } else {
                    AssetFormFieldError.PURCHASE_PRICE_TOO_LARGE
                }
                else -> if (required) AssetFormFieldError.CURRENT_VALUE_INVALID else AssetFormFieldError.PURCHASE_PRICE_INVALID
            },
        )
    }
}

private fun String.isZeroDecimal(): Boolean {
    if (!all { it.isDigit() || it == '.' || it == ',' }) return false
    if (count { it == '.' || it == ',' } > 1) return false
    val parts = split('.', ',')
    if (parts.any(String::isEmpty)) return false
    return parts.all { part -> part.all { it == '0' } }
}

private data class QuantityFieldResult(val quantity: AssetQuantity?, val error: AssetFormFieldError?)

private fun parseQuantity(input: String): QuantityFieldResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return QuantityFieldResult(null, null)
    if (!trimmed.all { it.isDigit() || it == '.' || it == ',' } || ('.' in trimmed && ',' in trimmed)) {
        return QuantityFieldResult(null, AssetFormFieldError.QUANTITY_INVALID)
    }
    val separator = when {
        ',' in trimmed -> ','
        '.' in trimmed -> '.'
        else -> null
    }
    val parts = if (separator == null) listOf(trimmed) else trimmed.split(separator)
    if (parts.size > 2 || parts.any(String::isEmpty) || parts.any { part -> !part.all(Char::isDigit) }) {
        return QuantityFieldResult(null, AssetFormFieldError.QUANTITY_INVALID)
    }
    val scale = parts.getOrNull(1)?.length ?: 0
    if (scale > 12) return QuantityFieldResult(null, AssetFormFieldError.QUANTITY_SCALE_EXCEEDED)
    val unscaled = parts.joinToString("").trimStart('0').ifEmpty { "0" }.toLongOrNull()
        ?: return QuantityFieldResult(null, AssetFormFieldError.QUANTITY_INVALID)
    return QuantityFieldResult(AssetQuantity(unscaled, scale), null)
}

private fun AssetQuantity.toInputText(): String {
    if (scale == 0) return unscaledValue.toString()
    val digits = unscaledValue.toString().padStart(scale + 1, '0')
    return "${digits.dropLast(scale)},${digits.takeLast(scale)}"
}
