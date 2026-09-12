package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.CreateAssetCommand
import com.feniqo.mobile.domain.model.UpdateAssetCommand

sealed interface AssetValidationResult<out T> {
    data class Valid<T>(val value: T) : AssetValidationResult<T>
    data class Invalid(val error: AssetValidationError) : AssetValidationResult<Nothing>
}

enum class AssetValidationError {
    NAME_BLANK,
    NAME_TOO_LONG,
    PURCHASE_PRICE_CURRENCY_MISMATCH,
    TRACKING_SYMBOL_REQUIRED,
    TRACKING_SYMBOL_TOO_LONG,
}

/** Platformdan bağımsız, kalıcı asset değerleri için doğrulama ve normalizasyon. */
object AssetValidationRules {
    const val MAX_NAME_LENGTH = 120
    const val MAX_TRACKING_SYMBOL_LENGTH = 32

    fun validateCreate(command: CreateAssetCommand): AssetValidationResult<CreateAssetCommand> =
        normalize(command.name, command.currentValue.currency.code, command.purchaseUnitPrice?.currency?.code, command.trackingSymbol, command.autoTrack) { name, symbol ->
            command.copy(name = name, trackingSymbol = symbol)
        }

    fun validateUpdate(command: UpdateAssetCommand): AssetValidationResult<UpdateAssetCommand> =
        normalize(command.name, command.currentValue.currency.code, command.purchaseUnitPrice?.currency?.code, command.trackingSymbol, command.autoTrack) { name, symbol ->
            command.copy(name = name, trackingSymbol = symbol)
        }

    fun applyUpdate(existing: Asset, command: UpdateAssetCommand): AssetValidationResult<Asset> {
        require(existing.id == command.id) { "Varlık kimliği güncelleme komutuyla eşleşmelidir." }
        return when (val validated = validateUpdate(command)) {
            is AssetValidationResult.Invalid -> validated
            is AssetValidationResult.Valid -> AssetValidationResult.Valid(
                existing.copy(
                    name = validated.value.name,
                    type = validated.value.type,
                    currentValue = validated.value.currentValue,
                    quantity = validated.value.quantity,
                    purchaseUnitPrice = validated.value.purchaseUnitPrice,
                    trackingSymbol = validated.value.trackingSymbol,
                    autoTrack = validated.value.autoTrack,
                ),
            )
        }
    }

    private fun <T> normalize(
        rawName: String,
        currentCurrency: String,
        purchaseCurrency: String?,
        rawSymbol: String?,
        autoTrack: Boolean,
        build: (String, String?) -> T,
    ): AssetValidationResult<T> {
        val name = rawName.trim()
        if (name.isEmpty()) return AssetValidationResult.Invalid(AssetValidationError.NAME_BLANK)
        if (name.length > MAX_NAME_LENGTH) return AssetValidationResult.Invalid(AssetValidationError.NAME_TOO_LONG)
        if (purchaseCurrency != null && purchaseCurrency != currentCurrency) {
            return AssetValidationResult.Invalid(AssetValidationError.PURCHASE_PRICE_CURRENCY_MISMATCH)
        }

        val symbol = rawSymbol?.trim()?.takeIf { it.isNotEmpty() }
        if (symbol != null && symbol.length > MAX_TRACKING_SYMBOL_LENGTH) {
            return AssetValidationResult.Invalid(AssetValidationError.TRACKING_SYMBOL_TOO_LONG)
        }
        if (autoTrack && symbol == null) {
            return AssetValidationResult.Invalid(AssetValidationError.TRACKING_SYMBOL_REQUIRED)
        }
        return AssetValidationResult.Valid(build(name, symbol))
    }
}
