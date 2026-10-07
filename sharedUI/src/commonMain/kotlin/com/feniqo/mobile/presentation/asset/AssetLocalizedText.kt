@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.asset

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.presentation.common.formatAssetQuantity
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 11 AssetFormFieldError enum değerinin StringResource eşlemesi.
 */
fun AssetFormFieldError.toLocalizedResource(): StringResource =
    when (this) {
        AssetFormFieldError.NAME_REQUIRED -> Res.string.asset_error_name_required
        AssetFormFieldError.NAME_TOO_LONG -> Res.string.asset_error_name_too_long
        AssetFormFieldError.CURRENT_VALUE_REQUIRED -> Res.string.asset_error_current_value_required
        AssetFormFieldError.CURRENT_VALUE_INVALID -> Res.string.asset_error_current_value_invalid
        AssetFormFieldError.CURRENT_VALUE_TOO_LARGE -> Res.string.asset_error_current_value_too_large
        AssetFormFieldError.QUANTITY_INVALID -> Res.string.asset_error_quantity_invalid
        AssetFormFieldError.QUANTITY_SCALE_EXCEEDED -> Res.string.asset_error_quantity_scale_exceeded
        AssetFormFieldError.PURCHASE_PRICE_INVALID -> Res.string.asset_error_purchase_price_invalid
        AssetFormFieldError.PURCHASE_PRICE_TOO_LARGE -> Res.string.asset_error_purchase_price_too_large
        AssetFormFieldError.TRACKING_SYMBOL_REQUIRED -> Res.string.asset_error_tracking_symbol_required
        AssetFormFieldError.TRACKING_SYMBOL_TOO_LONG -> Res.string.asset_error_tracking_symbol_too_long
    }

@Composable
fun AssetFormFieldError.toLocalizedText(): String = stringResource(toLocalizedResource())

@Composable
fun AssetFormFieldError.toDisplayText(): String = toLocalizedText()

/**
 * Varlık türü ad ve alt başlık eşlemesi.
 */
fun AssetType.toLocalizedName(): StringResource =
    when (this) {
        AssetType.CASH -> Res.string.asset_type_cash
        AssetType.CRYPTO -> Res.string.asset_type_crypto
        AssetType.STOCKS -> Res.string.asset_type_stocks
        AssetType.REAL_ESTATE -> Res.string.asset_type_real_estate
        AssetType.PRECIOUS_METALS -> Res.string.asset_type_precious_metals
        AssetType.OTHER -> Res.string.asset_type_other
    }

fun AssetType.toLocalizedSubtitle(): StringResource =
    when (this) {
        AssetType.CASH -> Res.string.asset_type_cash_subtitle
        AssetType.CRYPTO -> Res.string.asset_type_crypto_subtitle
        AssetType.STOCKS -> Res.string.asset_type_stocks_subtitle
        AssetType.REAL_ESTATE -> Res.string.asset_type_real_estate_subtitle
        AssetType.PRECIOUS_METALS -> Res.string.asset_type_precious_metals_subtitle
        AssetType.OTHER -> Res.string.asset_type_other_subtitle
    }

@Composable
fun AssetType.toDisplayLabel(): String = stringResource(toLocalizedName())

@Composable
fun AssetType.toLocalizedLabelText(): String = toDisplayLabel()

@Composable
fun AssetType.toSubtitle(): String = stringResource(toLocalizedSubtitle())

@Composable
fun AssetType.toLocalizedSubtitleText(): String = toSubtitle()

/**
 * Değer kaynağı etiketi eşlemesi.
 */
fun AssetValueSource.toLocalizedLabelResource(): StringResource =
    when (this) {
        AssetValueSource.MARKET_FRESH -> Res.string.asset_value_source_market_fresh
        AssetValueSource.MARKET_STALE -> Res.string.asset_value_source_market_stale
        AssetValueSource.MANUAL -> Res.string.asset_value_source_manual
    }

@Composable
fun AssetValueSource.toDisplayLabel(): String = stringResource(toLocalizedLabelResource())

@Composable
fun AssetValueSource.toLocalizedText(): String = toDisplayLabel()

/**
 * Net değer kaynak özeti eşlemesi.
 */
fun NetWorthSourceSummary.toLocalizedResource(): StringResource =
    when (this) {
        NetWorthSourceSummary.MARKET_FRESH_ONLY -> Res.string.asset_source_summary_fresh_only
        NetWorthSourceSummary.MARKET_AND_MANUAL -> Res.string.asset_source_summary_fresh_and_manual
        NetWorthSourceSummary.MARKET_STALE -> Res.string.asset_source_summary_stale
        NetWorthSourceSummary.MANUAL_ONLY -> Res.string.asset_source_summary_manual_only
    }

@Composable
fun NetWorthSourceSummary.toDisplayLabel(): String = stringResource(toLocalizedResource())

@Composable
fun NetWorthSourceSummary.toLocalizedText(): String = toDisplayLabel()

/**
 * AssetQuantity nesnesinin sayısal olarak tam 1 olup olmadığını kontrol eder.
 * Double/Float kullanmaz; yalnız Long tamsayı aritmetiğiyle çalışır.
 * Örn: 1 (scale 0, unscaled 1) -> true
 *      1.0 (scale 1, unscaled 10) -> true
 *      0 (scale 0, unscaled 0) -> false
 *      2 (scale 0, unscaled 2) -> false
 *      30 (scale 0, unscaled 30) -> false
 *      1.5 (scale 1, unscaled 15) -> false
 */
fun AssetQuantity.isExactlyOne(): Boolean {
    if (unscaledValue <= 0L) return false
    var v = unscaledValue
    var s = scale
    while (s > 0 && v % 10L == 0L) {
        v /= 10L
        s--
    }
    return s == 0 && v == 1L
}

/**
 * AssetQuantity için çoğul seçim miktarını çözümler.
 * 1 -> 1 ("one"), diğerleri (0, 2, 30, 1.5 vb.) -> 2 ("other").
 */
fun resolveAssetQuantityPluralCount(quantity: AssetQuantity): Int =
    if (quantity.isExactlyOne()) 1 else 2

/**
 * Varlık sayısı çoğul formatlayıcısı.
 */
@Composable
fun formatLocalizedAssetCount(count: Int): String = pluralStringResource(Res.plurals.asset_count_plural, count, count)

/**
 * Varlık birim miktarı çoğul formatlayıcısı.
 * Varsayılan count değeri yoktur; çoğul seçimi açıkça iletilir.
 */
@Composable
fun formatLocalizedUnitCount(unitText: String, count: Int): String =
    pluralStringResource(Res.plurals.asset_unit_count_plural, count, unitText)

/**
 * AssetQuantity üzerinden birim metni ve çoğul kurallarını çözümler.
 */
@Composable
fun formatLocalizedQuantityUnit(
    quantity: AssetQuantity,
    decimalSeparator: String,
): String {
    val unitText = formatAssetQuantity(quantity, decimalSeparator)
    val pluralCount = resolveAssetQuantityPluralCount(quantity)
    return pluralStringResource(Res.plurals.asset_unit_count_plural, pluralCount, unitText)
}

/**
 * Donut merkezi varlık etiketi (1 -> varlık/asset, 2+ -> varlık/assets).
 */
@Composable
fun formatLocalizedDonutAssetLabel(totalAssetCount: Int): String =
    pluralStringResource(Res.plurals.asset_donut_count_plural, totalAssetCount)

@Composable
fun assetFormNotFoundTitleText(): String = stringResource(Res.string.asset_form_not_found_title)

@Composable
fun assetFormNotFoundDescText(): String = stringResource(Res.string.asset_form_not_found_desc)

@Composable
fun assetFormErrorTitleText(): String = stringResource(Res.string.asset_form_error_title)

@Composable
fun assetFormLoadingText(): String = stringResource(Res.string.asset_form_loading)

@Composable
fun assetFormBackActionText(): String = stringResource(Res.string.asset_form_back_action)

@Composable
fun assetRouteInvalidIdTitleText(): String = stringResource(Res.string.asset_route_invalid_id_title)

@Composable
fun assetRouteInvalidIdDescText(): String = stringResource(Res.string.asset_route_invalid_id_desc)
